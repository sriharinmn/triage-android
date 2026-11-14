package io.github.sriharinmn.triage.data.demo.content

/** The main thread launching an activity, down to its `onCreate`. */
private val ACTIVITY_LAUNCH: List<DemoFrame> =
  ANDROID_ACTIVITY_TRANSACTION +
    listOf(
      DemoFrame("handleLaunchActivity", "android.app.ActivityThread", "ActivityThread.java", 4126, inApp = false),
      DemoFrame("performLaunchActivity", "android.app.ActivityThread", "ActivityThread.java", 3944, inApp = false),
      DemoFrame("callActivityOnCreate", "android.app.Instrumentation", "Instrumentation.java", 1477, inApp = false),
      DemoFrame("performCreate", "android.app.Activity", "Activity.java", 8960, inApp = false),
    )

/** The Android app's issues that turn up during the demo, in order. */
internal object AndroidArrivals {
  val all: List<DemoIssueSpec> =
    listOf(
      DemoIssueSpec(
        key = "android-arr-file-provider",
        projectSlug = SHOP_ANDROID,
        title =
          "SecurityException: Permission Denial: opening provider androidx.core.content.FileProvider " +
            "from ProcessRecord{4f2a9c1 23514:com.android.camera/u0a112}",
        culprit = "com.example.shop.reviews.PhotoPicker in launchCamera",
        level = "error",
        isUnhandled = true,
        ratePerHour = 0.3,
        userShare = 0.9,
        // Only phones whose camera app is com.android.camera, which is mostly one maker's.
        tags =
          androidTags(
            device = listOf("Redmi Note 13" to 0.5, "Redmi 13C" to 0.25, "Poco X6" to 0.15, "Redmi Note 12" to 0.1),
            os = listOf("Android 14" to 0.7, "Android 15" to 0.2, "Android 13" to 0.1),
          ),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "SecurityException",
                  value =
                    "Permission Denial: opening provider androidx.core.content.FileProvider " +
                      "from ProcessRecord{4f2a9c1 23514:com.android.camera/u0a112}",
                  module = "java.lang",
                  frames =
                    ANDROID_LOOPER +
                      listOf(
                        DemoFrame("run", "android.view.View\$PerformClick", "View.java", 31054, inApp = false),
                        DemoFrame("performClick", "android.view.View", "View.java", 8108, inApp = false),
                        DemoFrame(
                          "onViewCreated\$lambda\$4",
                          "com.example.shop.reviews.WriteReviewFragment",
                          "WriteReviewFragment.kt",
                          57,
                        ),
                        DemoFrame("launchCamera", "com.example.shop.reviews.PhotoPicker", "PhotoPicker.kt", 62),
                        DemoFrame(
                          "launch",
                          "androidx.activity.result.ActivityResultLauncher",
                          "ActivityResultLauncher.kt",
                          47,
                          inApp = false,
                        ),
                        DemoFrame("execStartActivity", "android.app.Instrumentation", "Instrumentation.java", 1880, inApp = false),
                        DemoFrame("readException", "android.os.Parcel", "Parcel.java", 3133, inApp = false),
                        DemoFrame("createExceptionOrNull", "android.os.Parcel", "Parcel.java", 3189, inApp = false),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(48, "navigation", type = "navigation", data = mapOf("from" to "Account", "to" to "ProductDetail")),
                DemoBreadcrumb(40, "ui.click", "Tapped \"Write a review\""),
                DemoBreadcrumb(39, "navigation", type = "navigation", data = mapOf("from" to "ProductDetail", "to" to "WriteReview")),
                DemoBreadcrumb(6, "ui.click", "Tapped \"Add photo\""),
                DemoBreadcrumb(1, "ui.click", "Tapped \"Take photo\""),
              ),
            contexts = androidContexts("Xiaomi", "Redmi Note 13", "14"),
            userIndex = 167,
          ),
      ),
      DemoIssueSpec(
        key = "android-arr-carousel-index",
        projectSlug = SHOP_ANDROID,
        title = "IndexOutOfBoundsException: Index 3 out of bounds for length 3",
        culprit = "com.example.shop.home.CarouselAdapter in onBindViewHolder",
        level = "fatal",
        isUnhandled = true,
        ratePerHour = 0.8,
        userShare = 0.8,
        tags = ANDROID_TAGS,
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "IndexOutOfBoundsException",
                  value = "Index 3 out of bounds for length 3",
                  module = "java.lang",
                  frames =
                    ANDROID_CHOREOGRAPHER +
                      listOf(
                        DemoFrame("performTraversals", "android.view.ViewRootImpl", "ViewRootImpl.java", 4230, inApp = false),
                        DemoFrame("onLayout", "androidx.recyclerview.widget.RecyclerView", "RecyclerView.java", 4744, inApp = false),
                        DemoFrame(
                          "dispatchLayoutStep2",
                          "androidx.recyclerview.widget.RecyclerView",
                          "RecyclerView.java",
                          4614,
                          inApp = false,
                        ),
                        DemoFrame("onLayoutChildren", "com.example.shop.home.PeekingLayoutManager", "PeekingLayoutManager.kt", 33),
                        DemoFrame(
                          "onLayoutChildren",
                          "androidx.recyclerview.widget.LinearLayoutManager",
                          "LinearLayoutManager.java",
                          668,
                          inApp = false,
                        ),
                      ) +
                      ANDROID_RECYCLER_BIND +
                      listOf(
                        DemoFrame(
                          "onBindViewHolder",
                          "com.example.shop.home.CarouselAdapter",
                          "CarouselAdapter.kt",
                          38,
                          context =
                            listOf(
                              37 to "  override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {",
                              38 to "    val banner = banners[position]",
                              39 to "    holder.bind(banner, showSeeAll = position == itemCount - 1)",
                            ),
                        ),
                        DemoFrame("get", "java.util.ArrayList", "ArrayList.java", 434, inApp = false),
                        DemoFrame("checkIndex", "java.util.Objects", "Objects.java", 385, inApp = false),
                        DemoFrame("outOfBounds", "jdk.internal.util.Preconditions", "Preconditions.java", 156, inApp = false),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(40, "navigation", type = "navigation", data = mapOf("from" to "Checkout", "to" to "OrderConfirmation")),
                DemoBreadcrumb(12, "ui.click", "Tapped \"Continue shopping\""),
                DemoBreadcrumb(11, "navigation", type = "navigation", data = mapOf("from" to "OrderConfirmation", "to" to "Home")),
                DemoBreadcrumb(
                  10,
                  "http",
                  type = "http",
                  data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/home/feed/", "status_code" to "200"),
                ),
                DemoBreadcrumb(
                  2,
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
            userIndex = 38,
          ),
      ),
      DemoIssueSpec(
        key = "android-arr-search-cancelled",
        projectSlug = SHOP_ANDROID,
        title = "JobCancellationException: StandaloneCoroutine was cancelled",
        culprit = "com.example.shop.search.SearchViewModel in search",
        level = "warning",
        isUnhandled = false,
        ratePerHour = 2.0,
        userShare = 0.4,
        tags =
          androidTags(
            device = listOf("OnePlus 12" to 0.28, "Pixel 8" to 0.27, "Galaxy S24" to 0.2, "Redmi Note 13" to 0.15, "Pixel 7a" to 0.1),
          ),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "JobCancellationException",
                  value = "StandaloneCoroutine was cancelled",
                  module = "kotlinx.coroutines",
                  frames =
                    ANDROID_LOOPER +
                      listOf(
                        DemoFrame(
                          "commitText",
                          "android.view.inputmethod.BaseInputConnection",
                          "BaseInputConnection.java",
                          922,
                          inApp = false,
                        ),
                        DemoFrame("sendAfterTextChanged", "android.widget.TextView", "TextView.java", 11391, inApp = false),
                        DemoFrame(
                          "afterTextChanged",
                          "com.example.shop.search.SearchFragment\$onViewCreated\$1",
                          "SearchFragment.kt",
                          48,
                        ),
                        DemoFrame("onQueryChanged", "com.example.shop.search.SearchViewModel", "SearchViewModel.kt", 39),
                        DemoFrame("search", "com.example.shop.search.SearchViewModel", "SearchViewModel.kt", 45),
                        DemoFrame("cancel", "kotlinx.coroutines.JobSupport", "JobSupport.kt", 1579, inApp = false),
                        DemoFrame(
                          "defaultCancellationException\$kotlinx_coroutines_core",
                          "kotlinx.coroutines.JobSupport",
                          "JobSupport.kt",
                          681,
                          inApp = false,
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(20, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Search")),
                DemoBreadcrumb(12, "ui.click", "Tapped \"Search products\""),
                DemoBreadcrumb(
                  9,
                  "http",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.shop.example.com/api/v2/search/suggestions/?q=ra",
                      "status_code" to "200",
                    ),
                ),
                DemoBreadcrumb(
                  6,
                  "http",
                  type = "http",
                  data =
                    mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/search/?q=rain", "status_code" to "200"),
                ),
                DemoBreadcrumb(
                  3,
                  "http",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.shop.example.com/api/v2/search/suggestions/?q=rain+j",
                      "status_code" to "200",
                    ),
                ),
              ),
            contexts = androidContexts("OnePlus", "OnePlus 12", "15"),
            userIndex = 92,
          ),
      ),
      DemoIssueSpec(
        key = "android-arr-webview",
        projectSlug = SHOP_ANDROID,
        title = "RuntimeException: WebView not available: Failed to load WebView provider: No WebView installed",
        culprit = "com.example.shop.help.HelpCenterActivity in onCreate",
        level = "error",
        isUnhandled = true,
        ratePerHour = 0.2,
        userShare = 0.95,
        tags =
          androidTags(
            device = listOf("Galaxy A15" to 0.4, "Redmi Note 13" to 0.3, "Pixel 7a" to 0.2, "Galaxy S24" to 0.1),
            os = listOf("Android 14" to 0.65, "Android 15" to 0.35),
          ),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "WebViewFactory\$MissingWebViewPackageException",
                  value = "Failed to load WebView provider: No WebView installed",
                  module = "android.webkit",
                  frames =
                    ACTIVITY_LAUNCH +
                      listOf(
                        DemoFrame("onCreate", "com.example.shop.help.HelpCenterActivity", "HelpCenterActivity.kt", 41),
                        DemoFrame("createWebView", "com.example.shop.help.HelpCenterActivity", "HelpCenterActivity.kt", 62),
                        DemoFrame("<init>", "android.webkit.WebView", "WebView.java", 446, inApp = false),
                        DemoFrame("getProvider", "android.webkit.WebViewFactory", "WebViewFactory.java", 289, inApp = false),
                        DemoFrame(
                          "getWebViewContextAndSetProvider",
                          "android.webkit.WebViewFactory",
                          "WebViewFactory.java",
                          431,
                          inApp = false,
                        ),
                      ),
                ),
                DemoException(
                  type = "RuntimeException",
                  value = "WebView not available: Failed to load WebView provider: No WebView installed",
                  module = "java.lang",
                  frames =
                    ACTIVITY_LAUNCH +
                      DemoFrame(
                        "onCreate",
                        "com.example.shop.help.HelpCenterActivity",
                        "HelpCenterActivity.kt",
                        43,
                        context =
                          listOf(
                            42 to "    } catch (e: AndroidRuntimeException) {",
                            43 to "      throw RuntimeException(\"WebView not available: \${e.cause?.message}\", e.cause)",
                            44 to "    }",
                          ),
                      ),
                ),
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(30, "app.lifecycle", "Moved to foreground"),
                DemoBreadcrumb(29, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Account")),
                DemoBreadcrumb(
                  27,
                  "http",
                  type = "http",
                  data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/me/", "status_code" to "200"),
                ),
                DemoBreadcrumb(2, "ui.click", "Tapped \"Help center\""),
              ),
            contexts = androidContexts("Samsung", "Galaxy A15", "14"),
            userIndex = 204,
          ),
      ),
      DemoIssueSpec(
        key = "android-arr-currency",
        projectSlug = SHOP_ANDROID,
        title = "IllegalArgumentException: Unsupported currency code: XYZ",
        culprit = "com.example.shop.price.PriceFormatter in format",
        level = "error",
        isUnhandled = false,
        ratePerHour = 0.6,
        userShare = 0.7,
        // A test product in the staging catalog; a few slipped into a production import.
        tags =
          androidTags(
            device = listOf("Pixel 8" to 0.6, "Galaxy S24" to 0.25, "Pixel 7a" to 0.15),
            os = listOf("Android 16" to 0.6, "Android 15" to 0.4),
            environment = listOf("staging" to 0.6, "production" to 0.4),
          ),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "IllegalArgumentException",
                  value = "Unsupported currency code: XYZ",
                  module = "java.lang",
                  frames =
                    ANDROID_GRID_SCROLL +
                      listOf(
                        DemoFrame("onBindViewHolder", "com.example.shop.catalog.ProductGridAdapter", "ProductGridAdapter.kt", 69),
                        DemoFrame(
                          "format",
                          "com.example.shop.price.PriceFormatter",
                          "PriceFormatter.kt",
                          28,
                          context =
                            listOf(
                              27 to "  fun format(amountMinor: Long, code: String): String {",
                              28 to "    require(code in currencies) { \"Unsupported currency code: \$code\" }",
                              29 to "    return formatterFor(code).format(amountMinor)",
                            ),
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(33, "navigation", type = "navigation", data = mapOf("from" to "Home", "to" to "Search")),
                DemoBreadcrumb(
                  31,
                  "http",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.staging.shop.example.com/api/v2/products/?collection=new-arrivals",
                      "status_code" to "200",
                    ),
                ),
                DemoBreadcrumb(8, "ui.scroll", "Scrolled product_grid down"),
                DemoBreadcrumb(
                  2,
                  "http",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.staging.shop.example.com/api/v2/products/?collection=new-arrivals&page=2",
                      "status_code" to "200",
                    ),
                ),
              ),
            contexts = androidContexts("Google", "Pixel 8", "16"),
            userIndex = 3,
          ),
      ),
    )
}
