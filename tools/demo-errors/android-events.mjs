// Crashes from a pretend Android shop app. Node can't throw Java exceptions, so these are hand-built events in the
// shape the Sentry Android SDK sends. Each scenario becomes one Sentry issue.

const DEVICES = [
  { manufacturer: "Google", brand: "google", model: "Pixel 7", family: "Pixel", os: "14" },
  { manufacturer: "Google", brand: "google", model: "Pixel 8 Pro", family: "Pixel", os: "15" },
  { manufacturer: "samsung", brand: "samsung", model: "SM-S911B", family: "Galaxy S23", os: "14" },
  { manufacturer: "OnePlus", brand: "OnePlus", model: "CPH2449", family: "OnePlus 11", os: "13" },
  { manufacturer: "Xiaomi", brand: "Redmi", model: "23021RAAEG", family: "Redmi Note 12", os: "13" },
];

/** One frame of a Java stack trace, written top (crash site) first like `at module.function(file:line)`. */
function at(qualifiedFunction, file, line, inApp = false) {
  const dot = qualifiedFunction.lastIndexOf(".");
  return {
    module: qualifiedFunction.slice(0, dot),
    function: qualifiedFunction.slice(dot + 1),
    filename: file,
    lineno: line,
    in_app: inApp,
  };
}

const MAIN_LOOP = [
  at("android.os.Handler.handleCallback", "Handler.java", 959),
  at("android.os.Handler.dispatchMessage", "Handler.java", 100),
  at("android.os.Looper.loopOnce", "Looper.java", 232),
  at("android.os.Looper.loop", "Looper.java", 317),
  at("android.app.ActivityThread.main", "ActivityThread.java", 8705),
  at("java.lang.reflect.Method.invoke", "Method.java", -2),
  at("com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run", "RuntimeInit.java", 580),
  at("com.android.internal.os.ZygoteInit.main", "ZygoteInit.java", 886),
];

const COROUTINE_LOOP = [
  at("kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith", "ContinuationImpl.kt", 33),
  at("kotlinx.coroutines.DispatchedTask.run", "DispatchedTask.kt", 104),
  ...MAIN_LOOP,
];

function javaEvent({ type, value, frames, mechanism, threadName = "main" }) {
  const dot = type.lastIndexOf(".");
  const device = DEVICES[Math.floor(Math.random() * DEVICES.length)];
  return {
    platform: "java",
    exception: {
      values: [
        {
          type: dot >= 0 ? type.slice(dot + 1) : type,
          module: dot >= 0 ? type.slice(0, dot) : undefined,
          value,
          mechanism,
          thread_id: 1,
          // Sentry lists frames oldest call first.
          stacktrace: { frames: [...frames].reverse() },
        },
      ],
    },
    threads: { values: [{ id: 1, name: threadName, crashed: true, current: true }] },
    contexts: {
      device: {
        family: device.family,
        model: device.model,
        manufacturer: device.manufacturer,
        brand: device.brand,
        arch: "arm64-v8a",
        simulator: false,
      },
      os: { name: "Android", version: device.os },
      app: { app_identifier: "com.example.shop", app_name: "Example Shop", app_version: "2.3.1", app_build: "231" },
    },
  };
}

const CRASH = { type: "UncaughtExceptionHandler", handled: false };

const navigation = (from, to) => ({ category: "navigation", data: { from, to } });
const click = (view) => ({ category: "ui.click", message: view });
const http = (method, url, status) => ({ type: "http", category: "http", data: { method, url, status_code: status } });

export const androidScenarios = [
  {
    name: "npe-profile",
    count: 14,
    users: 9,
    userSeed: 1000,
    level: "fatal",
    environments: ["production"],
    newestHoursAgo: 0.2,
    oldestDaysAgo: 5,
    breadcrumbs: [click("profile_avatar"), navigation("HomeFragment", "ProfileFragment"), http("GET", "https://api.example-shop.test/v1/me", 401)],
    send: (Sentry) =>
      Sentry.captureEvent(
        javaEvent({
          type: "java.lang.NullPointerException",
          value:
            "Attempt to invoke virtual method 'java.lang.String com.example.shop.model.User.getDisplayName()' on a null object reference",
          mechanism: CRASH,
          frames: [
            at("com.example.shop.ui.profile.ProfileFragment.bindHeader", "ProfileFragment.kt", 88, true),
            at("com.example.shop.ui.profile.ProfileFragment.onViewCreated", "ProfileFragment.kt", 54, true),
            at("androidx.fragment.app.Fragment.performViewCreated", "Fragment.java", 3128),
            at("androidx.fragment.app.FragmentStateManager.createView", "FragmentStateManager.java", 552),
            at("androidx.fragment.app.FragmentStateManager.moveToExpectedState", "FragmentStateManager.java", 261),
            ...MAIN_LOOP,
          ],
        }),
      ),
  },
  {
    name: "fragment-detached",
    count: 5,
    users: 4,
    userSeed: 1100,
    level: "fatal",
    environments: ["production"],
    newestHoursAgo: 3,
    oldestDaysAgo: 4,
    breadcrumbs: [navigation("ProductFragment", "CartFragment"), click("remove_item"), navigation("CartFragment", "HomeFragment")],
    send: (Sentry) =>
      Sentry.captureEvent(
        javaEvent({
          type: "java.lang.IllegalStateException",
          value: "Fragment CartFragment{4a1b2c3} not attached to a context.",
          mechanism: CRASH,
          frames: [
            at("androidx.fragment.app.Fragment.requireContext", "Fragment.java", 972),
            at("com.example.shop.ui.cart.CartFragment.showRemovedItemToast", "CartFragment.kt", 131, true),
            at("com.example.shop.ui.cart.CartFragment$onItemRemoved$1.invokeSuspend", "CartFragment.kt", 118, true),
            ...COROUTINE_LOOP,
          ],
        }),
      ),
  },
  {
    name: "socket-timeout",
    count: 10,
    users: 8,
    userSeed: 1200,
    environments: ["production", "production", "staging"],
    newestHoursAgo: 0.7,
    oldestDaysAgo: 2,
    breadcrumbs: [click("refresh_catalog"), http("GET", "https://api.example-shop.test/v1/catalog", 0)],
    send: (Sentry) =>
      Sentry.captureEvent(
        javaEvent({
          type: "java.net.SocketTimeoutException",
          value: "timeout",
          mechanism: { type: "generic", handled: true },
          threadName: "DefaultDispatcher-worker-3",
          frames: [
            at("okhttp3.internal.http2.Http2Stream$StreamTimeout.newTimeoutException", "Http2Stream.kt", 675),
            at("okhttp3.internal.http2.Http2Stream$StreamTimeout.exitAndThrowIfTimedOut", "Http2Stream.kt", 684),
            at("okhttp3.internal.http2.Http2Stream.takeHeaders", "Http2Stream.kt", 143),
            at("okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain$okhttp", "RealCall.kt", 201),
            at("okhttp3.internal.connection.RealCall.execute", "RealCall.kt", 154),
            at("com.example.shop.data.CatalogRepository.refresh", "CatalogRepository.kt", 47, true),
            at("com.example.shop.data.CatalogRepository$refresh$2.invokeSuspend", "CatalogRepository.kt", 41, true),
            at("kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith", "ContinuationImpl.kt", 33),
            at("kotlinx.coroutines.DispatchedTask.run", "DispatchedTask.kt", 104),
            at("kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run", "CoroutineScheduler.kt", 584),
          ],
        }),
      ),
  },
  {
    name: "db-locked",
    count: 3,
    users: 3,
    userSeed: 1300,
    environments: ["production"],
    newestHoursAgo: 20,
    oldestDaysAgo: 3,
    send: (Sentry) =>
      Sentry.captureEvent(
        javaEvent({
          type: "android.database.sqlite.SQLiteDatabaseLockedException",
          value: "database is locked (code 5 SQLITE_BUSY)",
          mechanism: { type: "generic", handled: true },
          threadName: "WM.task-2",
          frames: [
            at("android.database.sqlite.SQLiteConnection.nativeExecuteForChangedRowCount", "SQLiteConnection.java", -2),
            at("android.database.sqlite.SQLiteConnection.executeForChangedRowCount", "SQLiteConnection.java", 961),
            at("android.database.sqlite.SQLiteSession.executeForChangedRowCount", "SQLiteSession.java", 754),
            at("androidx.room.EntityInsertionAdapter.insert", "EntityInsertionAdapter.kt", 62),
            at("com.example.shop.data.local.OrderDao_Impl.insert", "OrderDao_Impl.java", 96, true),
            at("com.example.shop.sync.OrderSyncWorker.doWork", "OrderSyncWorker.kt", 39, true),
            at("androidx.work.CoroutineWorker$startWork$1.invokeSuspend", "CoroutineWorker.kt", 68),
          ],
        }),
      ),
  },
  {
    name: "anr-home",
    count: 4,
    users: 4,
    userSeed: 1400,
    environments: ["production"],
    newestHoursAgo: 8,
    oldestDaysAgo: 6,
    breadcrumbs: [navigation("SplashActivity", "HomeActivity")],
    send: (Sentry) =>
      Sentry.captureEvent(
        javaEvent({
          type: "io.sentry.android.core.ApplicationNotResponding",
          value: "Application Not Responding for at least 5000 ms.",
          mechanism: { type: "ANR", handled: false },
          frames: [
            at("java.io.FileInputStream.read", "FileInputStream.java", 325),
            at("java.io.BufferedInputStream.read", "BufferedInputStream.java", 290),
            at("com.example.shop.ui.home.BannerCache.readFromDisk", "BannerCache.kt", 31, true),
            at("com.example.shop.ui.home.HomeViewModel.loadBanner", "HomeViewModel.kt", 72, true),
            at("com.example.shop.ui.home.HomeActivity.onResume", "HomeActivity.kt", 45, true),
            at("android.app.Activity.performResume", "Activity.java", 8960),
            ...MAIN_LOOP,
          ],
        }),
      ),
  },
  {
    name: "oom-gallery",
    count: 1,
    users: 1,
    userSeed: 1500,
    level: "fatal",
    environments: ["production"],
    newestHoursAgo: 40,
    oldestDaysAgo: 2,
    breadcrumbs: [click("product_image"), navigation("ProductFragment", "GalleryFragment")],
    send: (Sentry) =>
      Sentry.captureEvent(
        javaEvent({
          type: "java.lang.OutOfMemoryError",
          value: "Failed to allocate a 33177612 byte allocation with 25165824 free bytes and 24MB until OOM",
          mechanism: CRASH,
          frames: [
            at("android.graphics.BitmapFactory.nativeDecodeStream", "BitmapFactory.java", -2),
            at("android.graphics.BitmapFactory.decodeStream", "BitmapFactory.java", 790),
            at("com.example.shop.ui.gallery.ProductGalleryAdapter.decodeFullSize", "ProductGalleryAdapter.kt", 63, true),
            at("com.example.shop.ui.gallery.ProductGalleryAdapter.onBindViewHolder", "ProductGalleryAdapter.kt", 41, true),
            at("androidx.recyclerview.widget.RecyclerView$Adapter.bindViewHolder", "RecyclerView.java", 7254),
            ...MAIN_LOOP,
          ],
        }),
      ),
  },
];
