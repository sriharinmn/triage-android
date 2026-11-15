package io.github.sriharinmn.triage.data.demo.content

import java.time.Duration

// The Android app's shared pieces (also used by AndroidArrivals.kt), then its issues at the demo's start.

internal val ANDROID_TAGS: Map<String, List<Pair<String, Double>>> =
  mapOf(
    "environment" to listOf("production" to 0.9, "staging" to 0.1),
    "device" to listOf("Pixel 8" to 0.3, "Galaxy S24" to 0.25, "Redmi Note 13" to 0.2, "OnePlus 12" to 0.15, "Pixel 7a" to 0.1),
    "os" to listOf("Android 15" to 0.45, "Android 14" to 0.35, "Android 16" to 0.2),
  )

/**
 * [ANDROID_TAGS] with another mix of phones. An event shows each tag's most common value, so the first [device] and
 * [os] should be the phone in the issue's contexts.
 */
internal fun androidTags(
  device: List<Pair<String, Double>>,
  os: List<Pair<String, Double>> = ANDROID_TAGS.getValue("os"),
  environment: List<Pair<String, Double>> = ANDROID_TAGS.getValue("environment"),
): Map<String, List<Pair<String, Double>>> = mapOf("environment" to environment, "device" to device, "os" to os)

internal val ANDROID_CONTEXTS: Map<String, Map<String, String>> =
  mapOf(
    "device" to mapOf("model" to "Pixel 8", "manufacturer" to "Google"),
    "os" to mapOf("name" to "Android", "version" to "15"),
    "app" to mapOf("app_version" to "2.4.1", "app_build" to "241"),
  )

/** [ANDROID_CONTEXTS] on another phone. */
internal fun androidContexts(manufacturer: String, model: String, androidVersion: String): Map<String, Map<String, String>> =
  ANDROID_CONTEXTS +
    mapOf(
      "device" to mapOf("model" to model, "manufacturer" to manufacturer),
      "os" to mapOf("name" to "Android", "version" to androidVersion),
    )

/** The main thread's library frames under app code, oldest first. */
internal val ANDROID_MAIN_LOOP: List<DemoFrame> =
  listOf(
    DemoFrame("main", "com.android.internal.os.ZygoteInit", "ZygoteInit.java", 1042, inApp = false),
    DemoFrame("run", "com.android.internal.os.RuntimeInit\$MethodAndArgsCaller", "RuntimeInit.java", 548, inApp = false),
    DemoFrame("main", "android.app.ActivityThread", "ActivityThread.java", 8913, inApp = false),
    DemoFrame("loop", "android.os.Looper", "Looper.java", 317, inApp = false),
    DemoFrame("dispatchMessage", "android.os.Handler", "Handler.java", 99, inApp = false),
    DemoFrame("run", "kotlinx.coroutines.DispatchedTask", "DispatchedTask.kt", 108, inApp = false),
  )

/** [ANDROID_MAIN_LOOP] without the coroutine: the looper running a plain message or callback. */
internal val ANDROID_LOOPER: List<DemoFrame> = ANDROID_MAIN_LOOP.dropLast(1)

/** The main thread drawing a frame, down to the callback it runs: an animation (a fling) or a traversal (layout). */
internal val ANDROID_CHOREOGRAPHER: List<DemoFrame> =
  ANDROID_LOOPER +
    listOf(
      DemoFrame("run", "android.view.Choreographer\$FrameDisplayEventReceiver", "Choreographer.java", 1305, inApp = false),
      DemoFrame("doFrame", "android.view.Choreographer", "Choreographer.java", 1108, inApp = false),
      DemoFrame("doCallbacks", "android.view.Choreographer", "Choreographer.java", 1199, inApp = false),
      DemoFrame("run", "android.view.Choreographer\$CallbackRecord", "Choreographer.java", 1352, inApp = false),
    )

/** A RecyclerView's layout manager filling in a row, down to the adapter binding it. */
internal val ANDROID_RECYCLER_BIND: List<DemoFrame> =
  listOf(
    DemoFrame("fill", "androidx.recyclerview.widget.LinearLayoutManager", "LinearLayoutManager.java", 1591, inApp = false),
    DemoFrame("layoutChunk", "androidx.recyclerview.widget.LinearLayoutManager", "LinearLayoutManager.java", 1631, inApp = false),
    DemoFrame(
      "tryGetViewHolderForPositionByDeadline",
      "androidx.recyclerview.widget.RecyclerView\$Recycler",
      "RecyclerView.java",
      6707,
      inApp = false,
    ),
    DemoFrame(
      "tryBindViewHolderByDeadline",
      "androidx.recyclerview.widget.RecyclerView\$Recycler",
      "RecyclerView.java",
      6439,
      inApp = false,
    ),
    DemoFrame("bindViewHolder", "androidx.recyclerview.widget.RecyclerView\$Adapter", "RecyclerView.java", 7065, inApp = false),
  )

/** The product grid being flung on the main thread, binding rows as they scroll into view. */
internal val ANDROID_GRID_SCROLL: List<DemoFrame> =
  ANDROID_CHOREOGRAPHER +
    listOf(
      DemoFrame("run", "androidx.recyclerview.widget.RecyclerView\$ViewFlinger", "RecyclerView.java", 5487, inApp = false),
      DemoFrame("scrollStep", "androidx.recyclerview.widget.RecyclerView", "RecyclerView.java", 2078, inApp = false),
      DemoFrame(
        "scrollVerticallyBy",
        "androidx.recyclerview.widget.GridLayoutManager",
        "GridLayoutManager.java",
        439,
        inApp = false,
      ),
    ) +
    ANDROID_RECYCLER_BIND

/** A `Dispatchers.IO` or `Dispatchers.Default` thread resuming a coroutine. */
internal val ANDROID_BACKGROUND: List<DemoFrame> =
  listOf(
    DemoFrame("run", "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker", "CoroutineScheduler.kt", 749, inApp = false),
    DemoFrame(
      "runWorker",
      "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker",
      "CoroutineScheduler.kt",
      815,
      inApp = false,
    ),
    DemoFrame(
      "executeTask",
      "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker",
      "CoroutineScheduler.kt",
      829,
      inApp = false,
    ),
    DemoFrame("run", "kotlinx.coroutines.DispatchedTask", "DispatchedTask.kt", 108, inApp = false),
  )

/** An OkHttp dispatcher thread running an enqueued call into its interceptor chain. */
internal val ANDROID_OKHTTP_CALL: List<DemoFrame> =
  listOf(
    DemoFrame("run", "java.lang.Thread", "Thread.java", 1012, inApp = false),
    DemoFrame("run", "java.util.concurrent.ThreadPoolExecutor\$Worker", "ThreadPoolExecutor.java", 644, inApp = false),
    DemoFrame("runWorker", "java.util.concurrent.ThreadPoolExecutor", "ThreadPoolExecutor.java", 1145, inApp = false),
    DemoFrame("run", "okhttp3.internal.connection.RealCall\$AsyncCall", "RealCall.kt", 517, inApp = false),
    DemoFrame(
      "getResponseWithInterceptorChain\$okhttp",
      "okhttp3.internal.connection.RealCall",
      "RealCall.kt",
      201,
      inApp = false,
    ),
    DemoFrame("proceed", "okhttp3.internal.http.RealInterceptorChain", "RealInterceptorChain.kt", 109, inApp = false),
  )

/** The main thread carrying out an activity transaction: a launch, a new intent or a result. */
internal val ANDROID_ACTIVITY_TRANSACTION: List<DemoFrame> =
  ANDROID_LOOPER +
    listOf(
      DemoFrame("handleMessage", "android.app.ActivityThread\$H", "ActivityThread.java", 2593, inApp = false),
      DemoFrame(
        "execute",
        "android.app.servertransaction.TransactionExecutor",
        "TransactionExecutor.java",
        106,
        inApp = false,
      ),
    )

/** The image cache reading a full-size photo on whichever thread asked; the ANR and the OOM both end here. */
private val IMAGE_CACHE_DECODE =
  DemoFrame(
    "decodeFromDisk",
    "com.example.shop.catalog.ImageCache",
    "ImageCache.kt",
    112,
    context =
      listOf(
        111 to "    val file = diskCache.fileFor(key) ?: return null",
        112 to "    return BitmapFactory.decodeFile(file.path)",
        113 to "  }",
      ),
  )

internal object AndroidIssues {
  val starting: List<DemoStartingIssue> =
    listOf(
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-checkout-npe",
            projectSlug = SHOP_ANDROID,
            title =
              "NullPointerException: Attempt to invoke virtual method 'java.lang.String " +
                "com.example.shop.cart.CartItem.getSku()' on a null object reference",
            culprit = "com.example.shop.checkout.CheckoutViewModel in buildOrder",
            level = "fatal",
            isUnhandled = true,
            ratePerHour = 1.2,
            userShare = 0.6,
            tags = ANDROID_TAGS,
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "NullPointerException",
                      value =
                        "Attempt to invoke virtual method 'java.lang.String com.example.shop.cart.CartItem.getSku()' " +
                          "on a null object reference",
                      module = "java.lang",
                      frames =
                        ANDROID_MAIN_LOOP +
                          listOf(
                            DemoFrame(
                              "invokeSuspend",
                              "com.example.shop.checkout.CheckoutViewModel\$placeOrder\$1",
                              "CheckoutViewModel.kt",
                              88,
                            ),
                            DemoFrame(
                              "buildOrder",
                              "com.example.shop.checkout.CheckoutViewModel",
                              "CheckoutViewModel.kt",
                              131,
                              context =
                                listOf(
                                  130 to "    val lines = cart.items.map { OrderLine(it.sku, it.quantity) }",
                                  131 to "    val gift = OrderLine(cart.giftItem.sku, 1)",
                                  132 to "    return Order(lines + gift, cart.couponCode)",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(95, "navigation", type = "navigation", data = mapOf("from" to "ProductDetail", "to" to "Cart")),
                    DemoBreadcrumb(64, "ui.click", "Tapped \"Checkout\""),
                    DemoBreadcrumb(63, "navigation", type = "navigation", data = mapOf("from" to "Cart", "to" to "Checkout")),
                    DemoBreadcrumb(
                      40,
                      "http",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(12, "ui.click", "Tapped \"Place order\""),
                    DemoBreadcrumb(
                      1,
                      "http",
                      type = "http",
                      data =
                        mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/orders/preview/", "status_code" to "200"),
                    ),
                  ),
                contexts = ANDROID_CONTEXTS,
                userIndex = 17,
              ),
          ),
        firstSeenAgo = Duration.ofDays(21),
        assigneeId = "1002",
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-anr-main",
            projectSlug = SHOP_ANDROID,
            title = "ApplicationNotResponding: Application Not Responding for at least 5000 ms.",
            culprit = "com.example.shop.catalog.ImageCache in decodeFromDisk",
            level = "error",
            isUnhandled = true,
            ratePerHour = 0.4,
            userShare = 0.8,
            tags =
              androidTags(
                device =
                  listOf(
                    "Redmi Note 13" to 0.35,
                    "Galaxy A15" to 0.25,
                    "Pixel 7a" to 0.15,
                    "Galaxy S24" to 0.15,
                    "Pixel 8" to 0.1,
                  ),
                os = listOf("Android 14" to 0.5, "Android 15" to 0.35, "Android 16" to 0.15),
              ),
            event =
              DemoEventSpec(
                threads =
                  listOf(
                    DemoThread(
                      1,
                      "main",
                      crashed = true,
                      frames =
                        ANDROID_GRID_SCROLL +
                          listOf(
                            DemoFrame(
                              "onBindViewHolder",
                              "com.example.shop.catalog.ProductGridAdapter",
                              "ProductGridAdapter.kt",
                              74,
                            ),
                            IMAGE_CACHE_DECODE,
                            DemoFrame("decodeFile", "android.graphics.BitmapFactory", "BitmapFactory.java", 510, inApp = false),
                          ),
                    ),
                    DemoThread(
                      12,
                      "OkHttp Dispatcher",
                      crashed = false,
                      frames =
                        // Idle, waiting for the next call.
                        listOf(
                          DemoFrame("run", "java.lang.Thread", "Thread.java", 1012, inApp = false),
                          DemoFrame("run", "java.util.concurrent.ThreadPoolExecutor\$Worker", "ThreadPoolExecutor.java", 644, inApp = false),
                          DemoFrame("runWorker", "java.util.concurrent.ThreadPoolExecutor", "ThreadPoolExecutor.java", 1137, inApp = false),
                          DemoFrame("getTask", "java.util.concurrent.ThreadPoolExecutor", "ThreadPoolExecutor.java", 1086, inApp = false),
                          DemoFrame("poll", "java.util.concurrent.SynchronousQueue", "SynchronousQueue.java", 903, inApp = false),
                        ),
                    ),
                    DemoThread(
                      4,
                      "FinalizerDaemon",
                      crashed = false,
                      frames =
                        listOf(
                          DemoFrame("run", "java.lang.Daemons\$Daemon", "Daemons.java", 145, inApp = false),
                          DemoFrame("runInternal", "java.lang.Daemons\$FinalizerDaemon", "Daemons.java", 292, inApp = false),
                        ),
                    ),
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(41, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Search")),
                    DemoBreadcrumb(
                      38,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/?q=running+shoes",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(22, "ui.click", "Tapped \"Sort: Newest\""),
                    DemoBreadcrumb(
                      21,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/?q=running+shoes&sort=newest",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(5, "ui.scroll", "Flung product_grid down"),
                  ),
                contexts = androidContexts("Xiaomi", "Redmi Note 13", "14"),
                userIndex = 102,
              ),
          ),
        firstSeenAgo = Duration.ofDays(9),
        start = DemoStart.Escalating(Duration.ofHours(5)),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-socket-timeout",
            projectSlug = SHOP_ANDROID,
            title = "SocketTimeoutException: timeout",
            culprit = "com.example.shop.net.ApiClient in execute",
            level = "error",
            isUnhandled = false,
            ratePerHour = 6.0,
            userShare = 0.35,
            tags =
              androidTags(
                device =
                  listOf(
                    "OnePlus 12" to 0.3,
                    "Pixel 8" to 0.25,
                    "Galaxy S24" to 0.2,
                    "Redmi Note 13" to 0.15,
                    "Pixel 7a" to 0.1,
                  ),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "SocketTimeoutException",
                      value = "timeout",
                      module = "java.net",
                      frames =
                        ANDROID_BACKGROUND +
                          listOf(
                            DemoFrame(
                              "invokeSuspend",
                              "com.example.shop.catalog.CatalogRepository\$loadPage\$2",
                              "CatalogRepository.kt",
                              46,
                            ),
                            DemoFrame("execute", "com.example.shop.net.ApiClient", "ApiClient.kt", 58),
                            DemoFrame("execute", "retrofit2.OkHttpCall", "OkHttpCall.java", 204, inApp = false),
                            DemoFrame("execute", "okhttp3.internal.connection.RealCall", "RealCall.kt", 154, inApp = false),
                            DemoFrame(
                              "getResponseWithInterceptorChain\$okhttp",
                              "okhttp3.internal.connection.RealCall",
                              "RealCall.kt",
                              201,
                              inApp = false,
                            ),
                            DemoFrame(
                              "proceed",
                              "okhttp3.internal.http.RealInterceptorChain",
                              "RealInterceptorChain.kt",
                              109,
                              inApp = false,
                            ),
                            DemoFrame(
                              "intercept",
                              "okhttp3.internal.http.CallServerInterceptor",
                              "CallServerInterceptor.kt",
                              79,
                              inApp = false,
                            ),
                            DemoFrame(
                              "readResponseHeaders",
                              "okhttp3.internal.http1.Http1ExchangeCodec",
                              "Http1ExchangeCodec.kt",
                              178,
                              inApp = false,
                            ),
                            DemoFrame("read", "okio.AsyncTimeout\$source\$1", "AsyncTimeout.kt", 337, inApp = false),
                            DemoFrame("newTimeoutException", "okio.SocketAsyncTimeout", "JvmOkio.kt", 147, inApp = false),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(52, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Search")),
                    DemoBreadcrumb(
                      50,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/search/suggestions/?q=jack",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(34, "ui.click", "Tapped \"Jackets\""),
                    DemoBreadcrumb(
                      33,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/?category=jackets&page=1",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(12, "ui.scroll", "Scrolled product_grid down"),
                    DemoBreadcrumb(
                      1,
                      "http",
                      level = "error",
                      type = "http",
                      data =
                        mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/products/?category=jackets&page=2"),
                    ),
                  ),
                contexts = androidContexts("OnePlus", "OnePlus 12", "15"),
                userIndex = 54,
              ),
          ),
        firstSeenAgo = Duration.ofDays(40),
        assigneeId = "1003",
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-fragment-detached",
            projectSlug = SHOP_ANDROID,
            title = "IllegalStateException: Fragment ProductDetailFragment{8f3c2a1} not attached to a context.",
            culprit = "com.example.shop.product.ProductDetailFragment in onPriceLoaded",
            level = "fatal",
            isUnhandled = true,
            ratePerHour = 0.3,
            userShare = 0.9,
            tags =
              androidTags(
                device =
                  listOf(
                    "Galaxy S24" to 0.35,
                    "Pixel 8" to 0.25,
                    "OnePlus 12" to 0.15,
                    "Redmi Note 13" to 0.15,
                    "Pixel 7a" to 0.1,
                  ),
                os = listOf("Android 15" to 0.5, "Android 16" to 0.3, "Android 14" to 0.2),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "IllegalStateException",
                      value = "Fragment ProductDetailFragment{8f3c2a1} not attached to a context.",
                      module = "java.lang",
                      frames =
                        ANDROID_MAIN_LOOP +
                          listOf(
                            DemoFrame(
                              "invokeSuspend",
                              "com.example.shop.product.ProductDetailFragment\$refreshPrice\$1",
                              "ProductDetailFragment.kt",
                              198,
                            ),
                            DemoFrame(
                              "onPriceLoaded",
                              "com.example.shop.product.ProductDetailFragment",
                              "ProductDetailFragment.kt",
                              211,
                              context =
                                listOf(
                                  210 to "  private fun onPriceLoaded(price: Price) {",
                                  211 to "    val label = getString(R.string.price_with_tax, price.formatted)",
                                  212 to "    binding?.price?.text = label",
                                ),
                            ),
                            DemoFrame("getString", "androidx.fragment.app.Fragment", "Fragment.java", 1058, inApp = false),
                            DemoFrame("requireContext", "androidx.fragment.app.Fragment", "Fragment.java", 972, inApp = false),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(22, "navigation", type = "navigation", data = mapOf("from" to "Search", "to" to "ProductDetail")),
                    DemoBreadcrumb(
                      21,
                      "http",
                      type = "http",
                      data =
                        mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/products/88213/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(9, "ui.click", "Tapped \"Add to cart\""),
                    DemoBreadcrumb(
                      8,
                      "http",
                      type = "http",
                      data =
                        mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/cart/items/", "status_code" to "201"),
                    ),
                    DemoBreadcrumb(7, "navigation", type = "navigation", data = mapOf("from" to "ProductDetail", "to" to "Cart")),
                    DemoBreadcrumb(
                      2,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/88213/price/",
                          "status_code" to "200",
                        ),
                    ),
                  ),
                contexts = androidContexts("Samsung", "Galaxy S24", "15"),
                userIndex = 88,
              ),
          ),
        firstSeenAgo = Duration.ofDays(12),
        start = DemoStart.Regressed(Duration.ofHours(30)),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-oom-bitmap",
            projectSlug = SHOP_ANDROID,
            title = "OutOfMemoryError: Failed to allocate a 33177612 byte allocation with 25165824 free bytes and 28MB until OOM",
            culprit = "com.example.shop.catalog.ImageCache in decodeFromDisk",
            level = "fatal",
            isUnhandled = true,
            ratePerHour = 0.15,
            userShare = 0.95,
            tags =
              androidTags(
                device =
                  listOf(
                    "Redmi Note 13" to 0.4,
                    "Galaxy A15" to 0.3,
                    "Pixel 7a" to 0.15,
                    "Galaxy S24" to 0.1,
                    "Pixel 8" to 0.05,
                  ),
                os = listOf("Android 14" to 0.6, "Android 15" to 0.3, "Android 16" to 0.1),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "OutOfMemoryError",
                      value = "Failed to allocate a 33177612 byte allocation with 25165824 free bytes and 28MB until OOM",
                      module = "java.lang",
                      frames =
                        ANDROID_BACKGROUND +
                          listOf(
                            DemoFrame(
                              "invokeSuspend",
                              "com.example.shop.catalog.ImagePrefetcher\$prefetch\$1",
                              "ImagePrefetcher.kt",
                              37,
                            ),
                            IMAGE_CACHE_DECODE,
                            DemoFrame("decodeFile", "android.graphics.BitmapFactory", "BitmapFactory.java", 510, inApp = false),
                            DemoFrame("decodeStream", "android.graphics.BitmapFactory", "BitmapFactory.java", 669, inApp = false),
                            DemoFrame("nativeDecodeStream", "android.graphics.BitmapFactory", "BitmapFactory.java", inApp = false),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(70, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Search")),
                    DemoBreadcrumb(
                      66,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/?category=sneakers",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(30, "ui.scroll", "Scrolled product_grid down"),
                    DemoBreadcrumb(9, "device.event", "Low memory warning", level = "warning"),
                    DemoBreadcrumb(2, "ui.scroll", "Flung product_grid down"),
                  ),
                contexts = androidContexts("Xiaomi", "Redmi Note 13", "14"),
                userIndex = 131,
              ),
          ),
        firstSeenAgo = Duration.ofDays(30),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-promo-json",
            projectSlug = SHOP_ANDROID,
            title = "JsonDataException: Expected BEGIN_OBJECT but was STRING at path \$.promotions[0]",
            culprit = "com.example.shop.promo.PromoRepository in fetchBanners",
            level = "error",
            isUnhandled = false,
            ratePerHour = 2.5,
            userShare = 0.4,
            tags = ANDROID_TAGS,
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "JsonDataException",
                      value = "Expected BEGIN_OBJECT but was STRING at path \$.promotions[0]",
                      module = "com.squareup.moshi",
                      frames =
                        ANDROID_BACKGROUND +
                          listOf(
                            DemoFrame(
                              "invokeSuspend",
                              "com.example.shop.promo.PromoRepository\$fetchBanners\$1",
                              "PromoRepository.kt",
                              54,
                            ),
                            DemoFrame(
                              "fetchBanners",
                              "com.example.shop.promo.PromoRepository",
                              "PromoRepository.kt",
                              58,
                              context =
                                listOf(
                                  57 to "    val body = api.promotions(placement = \"home_top\").string()",
                                  58 to "    val response = responseAdapter.fromJson(body) ?: return emptyList()",
                                  59 to "    return response.promotions.map { it.toBanner() }",
                                ),
                            ),
                            DemoFrame("fromJson", "com.squareup.moshi.JsonAdapter", "JsonAdapter.java", 70, inApp = false),
                            DemoFrame(
                              "fromJson",
                              "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapter",
                              "KotlinJsonAdapter.kt",
                              99,
                              inApp = false,
                            ),
                            DemoFrame(
                              "fromJson",
                              "com.squareup.moshi.CollectionJsonAdapter",
                              "CollectionJsonAdapter.java",
                              81,
                              inApp = false,
                            ),
                            // The list element's adapter, expecting a promotion object.
                            DemoFrame(
                              "fromJson",
                              "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapter",
                              "KotlinJsonAdapter.kt",
                              74,
                              inApp = false,
                            ),
                            DemoFrame("beginObject", "com.squareup.moshi.JsonUtf8Reader", "JsonUtf8Reader.java", 172, inApp = false),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(140, "navigation", type = "navigation", data = mapOf("from" to "Account", "to" to "Home")),
                    DemoBreadcrumb(
                      139,
                      "http",
                      type = "http",
                      data =
                        mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/home/feed/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(60, "app.lifecycle", "Moved to background"),
                    DemoBreadcrumb(3, "app.lifecycle", "Moved to foreground"),
                    DemoBreadcrumb(
                      1,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/promotions/?placement=home_top",
                          "status_code" to "200",
                        ),
                    ),
                  ),
                contexts = ANDROID_CONTEXTS,
                userIndex = 9,
              ),
          ),
        firstSeenAgo = Duration.ofDays(2),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-payment-sheet",
            projectSlug = SHOP_ANDROID,
            title = "PaymentSheetException: Payment sheet closed before confirmation",
            culprit = "com.example.shop.checkout.PaymentLauncher in onResult",
            level = "warning",
            isUnhandled = false,
            ratePerHour = 4.0,
            userShare = 0.7,
            tags =
              androidTags(
                device =
                  listOf(
                    "Galaxy S24" to 0.3,
                    "Pixel 8" to 0.25,
                    "Redmi Note 13" to 0.2,
                    "OnePlus 12" to 0.15,
                    "Pixel 7a" to 0.1,
                  ),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "PaymentSheetException",
                      value = "Payment sheet closed before confirmation",
                      module = "com.example.shop.checkout",
                      frames =
                        ANDROID_ACTIVITY_TRANSACTION +
                          listOf(
                            DemoFrame("handleSendResult", "android.app.ActivityThread", "ActivityThread.java", 5704, inApp = false),
                            DemoFrame("dispatchActivityResult", "android.app.Activity", "Activity.java", 9113, inApp = false),
                            DemoFrame(
                              "dispatchResult",
                              "androidx.activity.result.ActivityResultRegistry",
                              "ActivityResultRegistry.kt",
                              349,
                              inApp = false,
                            ),
                            DemoFrame(
                              "onActivityResult",
                              "com.example.shop.checkout.PaymentLauncher\$register\$1",
                              "PaymentLauncher.kt",
                              31,
                            ),
                            DemoFrame("onResult", "com.example.shop.checkout.PaymentLauncher", "PaymentLauncher.kt", 49),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(150, "navigation", type = "navigation", data = mapOf("from" to "Cart", "to" to "Checkout")),
                    DemoBreadcrumb(
                      148,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/checkout/shipping-options/",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(97, "ui.click", "Tapped \"Pay now\""),
                    DemoBreadcrumb(
                      96,
                      "http",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "POST",
                          "url" to "https://api.shop.example.com/api/v2/payments/intents/",
                          "status_code" to "201",
                        ),
                    ),
                    DemoBreadcrumb(2, "ui.click", "Tapped \"Close\""),
                  ),
                contexts = androidContexts("Samsung", "Galaxy S24", "15"),
                userIndex = 76,
              ),
          ),
        firstSeenAgo = Duration.ofDays(50),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-db-locked",
            projectSlug = SHOP_ANDROID,
            title = "SQLiteDatabaseLockedException: database is locked (code 5 SQLITE_BUSY)",
            culprit = "com.example.shop.data.OrderDao_Impl in insertOrder",
            level = "error",
            isUnhandled = true,
            ratePerHour = 0.2,
            userShare = 0.9,
            tags =
              androidTags(
                device =
                  listOf(
                    "OnePlus 12" to 0.35,
                    "Galaxy S24" to 0.25,
                    "Pixel 8" to 0.2,
                    "Pixel 7a" to 0.1,
                    "Redmi Note 13" to 0.1,
                  ),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "SQLiteDatabaseLockedException",
                      value = "database is locked (code 5 SQLITE_BUSY)",
                      module = "android.database.sqlite",
                      frames =
                        ANDROID_BACKGROUND +
                          listOf(
                            DemoFrame(
                              "invokeSuspend",
                              "com.example.shop.data.OrderRepository\$saveConfirmedOrder\$2",
                              "OrderRepository.kt",
                              73,
                            ),
                            DemoFrame("insertOrder", "com.example.shop.data.OrderDao_Impl", "OrderDao_Impl.kt", 118),
                            DemoFrame(
                              "insertAndReturnId",
                              "androidx.room.EntityInsertionAdapter",
                              "EntityInsertionAdapter.kt",
                              101,
                              inApp = false,
                            ),
                            DemoFrame(
                              "executeInsert",
                              "androidx.sqlite.db.framework.FrameworkSQLiteStatement",
                              "FrameworkSQLiteStatement.kt",
                              42,
                              inApp = false,
                            ),
                            DemoFrame(
                              "executeInsert",
                              "android.database.sqlite.SQLiteStatement",
                              "SQLiteStatement.java",
                              92,
                              inApp = false,
                            ),
                            DemoFrame(
                              "executeForLastInsertedRowId",
                              "android.database.sqlite.SQLiteSession",
                              "SQLiteSession.java",
                              1002,
                              inApp = false,
                            ),
                            DemoFrame(
                              "nativeExecuteForLastInsertedRowId",
                              "android.database.sqlite.SQLiteConnection",
                              "SQLiteConnection.java",
                              inApp = false,
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(64, "navigation", type = "navigation", data = mapOf("from" to "Cart", "to" to "Checkout")),
                    DemoBreadcrumb(20, "ui.click", "Tapped \"Place order\""),
                    DemoBreadcrumb(
                      3,
                      "http",
                      type = "http",
                      data = mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/orders/", "status_code" to "201"),
                    ),
                    DemoBreadcrumb(
                      2,
                      "navigation",
                      type = "navigation",
                      data = mapOf("from" to "Checkout", "to" to "OrderConfirmation"),
                    ),
                    DemoBreadcrumb(1, "work", "OrderHistorySyncWorker started"),
                  ),
                contexts = androidContexts("OnePlus", "OnePlus 12", "15"),
                userIndex = 143,
              ),
          ),
        firstSeenAgo = Duration.ofDays(18),
        start = DemoStart.Resolved(Duration.ofDays(6)),
        assigneeId = "1001",
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-deeplink-number",
            projectSlug = SHOP_ANDROID,
            title = "NumberFormatException: For input string: \"summer-sale\"",
            culprit = "com.example.shop.nav.DeepLinkRouter in parseProductId",
            level = "error",
            isUnhandled = false,
            ratePerHour = 0.8,
            userShare = 0.5,
            tags = ANDROID_TAGS,
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "NumberFormatException",
                      value = "For input string: \"summer-sale\"",
                      module = "java.lang",
                      frames =
                        ANDROID_ACTIVITY_TRANSACTION +
                          listOf(
                            DemoFrame("handleNewIntent", "android.app.ActivityThread", "ActivityThread.java", 4383, inApp = false),
                            DemoFrame(
                              "callActivityOnNewIntent",
                              "android.app.Instrumentation",
                              "Instrumentation.java",
                              1653,
                              inApp = false,
                            ),
                            DemoFrame("onNewIntent", "com.example.shop.MainActivity", "MainActivity.kt", 64),
                            DemoFrame("route", "com.example.shop.nav.DeepLinkRouter", "DeepLinkRouter.kt", 41),
                            DemoFrame(
                              "parseProductId",
                              "com.example.shop.nav.DeepLinkRouter",
                              "DeepLinkRouter.kt",
                              90,
                              context =
                                listOf(
                                  89 to "    val segment = uri.pathSegments.getOrNull(1) ?: return null",
                                  90 to "    return segment.toLong()",
                                  91 to "  }",
                                ),
                            ),
                            DemoFrame("parseLong", "java.lang.Long", "Long.java", 711, inApp = false),
                            DemoFrame(
                              "forInputString",
                              "java.lang.NumberFormatException",
                              "NumberFormatException.java",
                              67,
                              inApp = false,
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(1320, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Cart")),
                    DemoBreadcrumb(
                      1318,
                      "http",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(1240, "app.lifecycle", "Moved to background"),
                    DemoBreadcrumb(4, "app.lifecycle", "Moved to foreground"),
                    DemoBreadcrumb(
                      3,
                      "deeplink",
                      "Opened https://shop.example.com/p/summer-sale",
                      data = mapOf("url" to "https://shop.example.com/p/summer-sale"),
                    ),
                  ),
                contexts = ANDROID_CONTEXTS,
                userIndex = 61,
              ),
          ),
        firstSeenAgo = Duration.ofHours(26),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-cart-sync",
            projectSlug = SHOP_ANDROID,
            title = "CartSyncException: Server cart version 14 is newer than local version 12",
            culprit = "com.example.shop.cart.CartSyncWorker in doWork",
            level = "warning",
            isUnhandled = false,
            ratePerHour = 3.0,
            userShare = 0.3,
            tags = ANDROID_TAGS,
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "CartSyncException",
                      value = "Server cart version 14 is newer than local version 12",
                      module = "com.example.shop.cart",
                      frames =
                        ANDROID_BACKGROUND +
                          listOf(
                            DemoFrame("invokeSuspend", "com.example.shop.cart.CartSyncWorker\$doWork\$1", "CartSyncWorker.kt", 29),
                            DemoFrame(
                              "doWork",
                              "com.example.shop.cart.CartSyncWorker",
                              "CartSyncWorker.kt",
                              42,
                              context =
                                listOf(
                                  41 to "    if (remote.version > local.version) {",
                                  42 to "      reporter.warning(CartSyncException(remote.version, local.version))",
                                  43 to "      cartStore.replaceWith(remote)",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(420, "ui.click", "Tapped \"Add to cart\""),
                    DemoBreadcrumb(
                      419,
                      "http",
                      level = "error",
                      type = "http",
                      data =
                        mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/cart/items/", "status_code" to "503"),
                    ),
                    DemoBreadcrumb(400, "app.lifecycle", "Moved to background"),
                    DemoBreadcrumb(3, "work", "CartSyncWorker started"),
                    DemoBreadcrumb(
                      2,
                      "http",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                  ),
                contexts = ANDROID_CONTEXTS,
                userIndex = 23,
              ),
          ),
        firstSeenAgo = Duration.ofDays(33),
        assigneeId = "1001",
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "android-token-refresh",
            projectSlug = SHOP_ANDROID,
            title = "HttpException: HTTP 401 Unauthorized",
            culprit = "com.example.shop.auth.TokenAuthenticator in authenticate",
            level = "error",
            isUnhandled = false,
            ratePerHour = 1.5,
            userShare = 0.6,
            tags =
              androidTags(
                device =
                  listOf(
                    "Galaxy S24" to 0.3,
                    "Pixel 8" to 0.3,
                    "OnePlus 12" to 0.2,
                    "Redmi Note 13" to 0.1,
                    "Pixel 7a" to 0.1,
                  ),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "HttpException",
                      value = "HTTP 401 Unauthorized",
                      module = "retrofit2",
                      frames =
                        ANDROID_OKHTTP_CALL +
                          listOf(
                            DemoFrame("intercept", "com.example.shop.net.ShopHeadersInterceptor", "ShopHeadersInterceptor.kt", 27),
                            DemoFrame(
                              "proceed",
                              "okhttp3.internal.http.RealInterceptorChain",
                              "RealInterceptorChain.kt",
                              109,
                              inApp = false,
                            ),
                            DemoFrame(
                              "intercept",
                              "okhttp3.internal.http.RetryAndFollowUpInterceptor",
                              "RetryAndFollowUpInterceptor.kt",
                              96,
                              inApp = false,
                            ),
                            DemoFrame(
                              "followUpRequest",
                              "okhttp3.internal.http.RetryAndFollowUpInterceptor",
                              "RetryAndFollowUpInterceptor.kt",
                              241,
                              inApp = false,
                            ),
                            DemoFrame("authenticate", "com.example.shop.auth.TokenAuthenticator", "TokenAuthenticator.kt", 40),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(9, "app.lifecycle", "Moved to foreground"),
                    DemoBreadcrumb(8, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Account")),
                    DemoBreadcrumb(4, "ui.click", "Tapped \"My orders\""),
                    DemoBreadcrumb(
                      3,
                      "http",
                      level = "warning",
                      type = "http",
                      data =
                        mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/me/orders/", "status_code" to "401"),
                    ),
                    DemoBreadcrumb(
                      1,
                      "http",
                      level = "warning",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "POST",
                          "url" to "https://api.shop.example.com/api/v2/auth/token/refresh/",
                          "status_code" to "401",
                        ),
                    ),
                  ),
                contexts = androidContexts("Samsung", "Galaxy S24", "15"),
                userIndex = 115,
              ),
          ),
        firstSeenAgo = Duration.ofDays(15),
        assigneeId = "1004",
      ),
    )
}
