package io.github.sriharinmn.triage.data.demo.content

import java.time.Duration

// The web storefront's shared pieces (also used by WebArrivals.kt), then its issues at the demo's start.

internal val WEB_TAGS: Map<String, List<Pair<String, Double>>> =
  mapOf(
    "environment" to listOf("production" to 0.85, "staging" to 0.15),
    "browser" to listOf("Chrome 131" to 0.55, "Safari 18.1" to 0.25, "Firefox 133" to 0.12, "Edge 131" to 0.08),
    "os" to listOf("Windows 11" to 0.4, "macOS 15" to 0.25, "iOS 18" to 0.2, "Android 15" to 0.15),
    "url" to listOf("https://shop.example.com/cart" to 0.6, "https://shop.example.com/checkout" to 0.4),
  )

/**
 * [WEB_TAGS] for another page or mix of browsers. An event shows each tag's most common value, so the first [url] should
 * be the event's page and the first [browser] and [os] the ones in its contexts.
 */
internal fun webTags(
  url: List<Pair<String, Double>>,
  browser: List<Pair<String, Double>> = WEB_TAGS.getValue("browser"),
  os: List<Pair<String, Double>> = WEB_TAGS.getValue("os"),
): Map<String, List<Pair<String, Double>>> =
  mapOf("environment" to WEB_TAGS.getValue("environment"), "browser" to browser, "os" to os, "url" to url)

// Browser and OS mixes led by Safari on a Mac (for SAFARI_MAC_CONTEXTS) and on an iPhone (SAFARI_IPHONE_CONTEXTS).
internal val MAC_BROWSERS = listOf("Safari 18.1" to 0.4, "Chrome 131" to 0.35, "Firefox 133" to 0.15, "Edge 131" to 0.1)
internal val MAC_OSES = listOf("macOS 15" to 0.45, "Windows 11" to 0.35, "iOS 18" to 0.2)
internal val PHONE_BROWSERS = listOf("Mobile Safari 18.1" to 0.55, "Chrome Mobile 131" to 0.3, "Chrome 131" to 0.15)
internal val PHONE_OSES = listOf("iOS 18" to 0.55, "Android 15" to 0.3, "Windows 11" to 0.15)

internal val WEB_CONTEXTS: Map<String, Map<String, String>> =
  mapOf(
    "browser" to mapOf("name" to "Chrome", "version" to "131.0.0"),
    "os" to mapOf("name" to "Windows", "version" to "11"),
  )

internal val SAFARI_MAC_CONTEXTS = webContexts("Safari", "18.1", "macOS", "15.1")
internal val SAFARI_IPHONE_CONTEXTS = webContexts("Mobile Safari", "18.1", "iOS", "18.1")
internal val FIREFOX_WINDOWS_CONTEXTS = webContexts("Firefox", "133.0", "Windows", "11")

private fun webContexts(browser: String, browserVersion: String, os: String, osVersion: String): Map<String, Map<String, String>> =
  mapOf(
    "browser" to mapOf("name" to browser, "version" to browserVersion),
    "os" to mapOf("name" to os, "version" to osVersion),
  )

internal const val REACT_DOM_JS = "node_modules/react-dom/cjs/react-dom-client.production.js"

/** React's frames under a component that threw while rendering, oldest first. */
internal val REACT_RENDER: List<DemoFrame> =
  listOf(
    DemoFrame("performWorkOnRoot", filename = "node_modules/react-dom/cjs/react-dom-client.production.js", lineNo = 14_802, colNo = 9, inApp = false),
    DemoFrame("renderRootSync", filename = "node_modules/react-dom/cjs/react-dom-client.production.js", lineNo = 14_210, colNo = 7, inApp = false),
    DemoFrame("beginWork", filename = "node_modules/react-dom/cjs/react-dom-client.production.js", lineNo = 8_120, colNo = 16, inApp = false),
  )

/** React dispatching a click or keystroke to a component's handler, oldest first. */
internal val REACT_EVENT: List<DemoFrame> =
  listOf(
    DemoFrame("dispatchDiscreteEvent", filename = REACT_DOM_JS, lineNo = 16_947, colNo = 7, inApp = false),
    DemoFrame("dispatchEvent", filename = REACT_DOM_JS, lineNo = 16_990, colNo = 9, inApp = false),
    DemoFrame("processDispatchQueue", filename = REACT_DOM_JS, lineNo = 16_402, colNo = 11, inApp = false),
    DemoFrame("callCallback", filename = REACT_DOM_JS, lineNo = 16_335, colNo = 14, inApp = false),
  )

/** React running a component's effects after a render, oldest first. */
internal val REACT_EFFECTS: List<DemoFrame> =
  listOf(
    DemoFrame("flushPassiveEffects", filename = REACT_DOM_JS, lineNo = 15_512, colNo = 16, inApp = false),
    DemoFrame("commitPassiveMountOnFiber", filename = REACT_DOM_JS, lineNo = 12_870, colNo = 11, inApp = false),
    DemoFrame("commitHookEffectListMount", filename = REACT_DOM_JS, lineNo = 11_094, colNo = 29, inApp = false),
  )

/**
 * The cause Sentry's React SDK links to an error that React caught: the component stack, from the app's root down to
 * [components], typed "React ErrorBoundary <type>".
 */
internal fun reactComponentStack(type: String, value: String, vararg components: DemoFrame): DemoException =
  DemoException(
    type = "React ErrorBoundary $type",
    value = value,
    frames =
      listOf(
        DemoFrame("App", filename = "src/App.tsx", lineNo = 24, colNo = 5),
        DemoFrame("AppShell", filename = "src/components/AppShell.tsx", lineNo = 31, colNo = 7),
      ) + components,
  )

private const val HYDRATION_FAILED = "Hydration failed because the initial UI does not match what was rendered on the server."

internal object WebIssues {
  val starting: List<DemoStartingIssue> =
    listOf(
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-price-undefined",
            projectSlug = SHOP_WEB,
            title = "TypeError: Cannot read properties of undefined (reading 'price')",
            culprit = "src/components/CartSummary.tsx in lineTotal",
            level = "error",
            isUnhandled = true,
            ratePerHour = 8.0,
            userShare = 0.6,
            tags = WEB_TAGS,
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "TypeError",
                      value = "Cannot read properties of undefined (reading 'price')",
                      frames =
                        REACT_RENDER +
                          listOf(
                            DemoFrame("CartSummary", filename = "src/components/CartSummary.tsx", lineNo = 42, colNo = 31),
                            DemoFrame(
                              "lineTotal",
                              filename = "src/components/CartSummary.tsx",
                              lineNo = 18,
                              colNo = 27,
                              context =
                                listOf(
                                  17 to "function lineTotal(line: CartLine) {",
                                  18 to "  return line.quantity * line.variant.price;",
                                  19 to "}",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(48, "navigation", type = "navigation", data = mapOf("from" to "/products/canvas-tote", "to" to "/cart")),
                    DemoBreadcrumb(
                      47,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(20, "ui.click", "button.quantity-increase"),
                    DemoBreadcrumb(
                      19,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "PATCH", "url" to "https://api.shop.example.com/api/v2/cart/lines/88/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(2, "console", "Variant 4412 is no longer sold; removing it from the cart", level = "warning"),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/cart"),
                contexts = WEB_CONTEXTS,
                userIndex = 42,
              ),
          ),
        firstSeenAgo = Duration.ofDays(28),
        assigneeId = "1005",
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-chunk-load",
            projectSlug = SHOP_WEB,
            title = "ChunkLoadError: Loading chunk 742 failed.",
            culprit = "webpack/runtime/jsonp chunk loading in __webpack_require__.f.j",
            level = "error",
            isUnhandled = true,
            ratePerHour = 3.5,
            userShare = 0.9,
            // Pages opened before a deploy ask for chunks the deploy removed.
            tags =
              webTags(
                url =
                  listOf(
                    "https://shop.example.com/products/linen-shirt" to 0.3,
                    "https://shop.example.com/" to 0.25,
                    "https://shop.example.com/cart" to 0.25,
                    "https://shop.example.com/search" to 0.2,
                  ),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "ChunkLoadError",
                      value = "Loading chunk 742 failed.\n(missing: https://shop.example.com/static/js/742.c3e1b9f0.chunk.js)",
                      frames =
                        REACT_EVENT +
                          listOf(
                            DemoFrame("handleNavClick", filename = "src/components/AppShell.tsx", lineNo = 52, colNo = 5),
                            DemoFrame(
                              "loadAccountOrders",
                              filename = "src/routes.tsx",
                              lineNo = 31,
                              colNo = 3,
                              context =
                                listOf(
                                  30 to "const loadAccountOrders = () =>",
                                  31 to "  import(\"./pages/account/OrdersPage\");",
                                  32 to "",
                                ),
                            ),
                            DemoFrame(
                              "__webpack_require__.e",
                              filename = "webpack/runtime/ensure chunk",
                              lineNo = 6,
                              colNo = 30,
                              inApp = false,
                            ),
                            DemoFrame(
                              "__webpack_require__.f.j",
                              filename = "webpack/runtime/jsonp chunk loading",
                              lineNo = 27,
                              colNo = 21,
                              inApp = false,
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(9_120, "navigation", type = "navigation", data = mapOf("from" to "/", "to" to "/products/linen-shirt")),
                    DemoBreadcrumb(
                      9_119,
                      "fetch",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/linen-shirt/",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(9_064, "ui.click", "button.add-to-cart"),
                    DemoBreadcrumb(
                      9_063,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/cart/lines/", "status_code" to "201"),
                    ),
                    DemoBreadcrumb(2, "ui.click", "nav.app-shell a[href=\"/account/orders\"]"),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/products/linen-shirt"),
                contexts = WEB_CONTEXTS,
                userIndex = 12,
              ),
          ),
        firstSeenAgo = Duration.ofDays(6),
        start = DemoStart.Escalating(Duration.ofHours(7)),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-hydration",
            projectSlug = SHOP_WEB,
            title = "Error: $HYDRATION_FAILED",
            culprit = "src/pages/product/[slug].tsx in ProductPage",
            level = "error",
            isUnhandled = true,
            ratePerHour = 1.2,
            userShare = 0.7,
            tags =
              webTags(
                url =
                  listOf(
                    "https://shop.example.com/products/canvas-tote" to 0.35,
                    "https://shop.example.com/products/linen-shirt" to 0.25,
                    "https://shop.example.com/products/merino-beanie" to 0.2,
                    "https://shop.example.com/products/suede-chelsea-boot" to 0.2,
                  ),
                browser = MAC_BROWSERS,
                os = MAC_OSES,
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    reactComponentStack(
                      "Error",
                      HYDRATION_FAILED,
                      DemoFrame("ProductPage", filename = "src/pages/product/[slug].tsx", lineNo = 22, colNo = 8),
                    ),
                    DemoException(
                      type = "Error",
                      value = HYDRATION_FAILED,
                      frames =
                        REACT_RENDER +
                          listOf(
                            // The server renders in UTC, so shoppers in other time zones can get the other note.
                            DemoFrame(
                              "ProductPage",
                              filename = "src/pages/product/[slug].tsx",
                              lineNo = 48,
                              colNo = 11,
                              context =
                                listOf(
                                  47 to "      <p className=\"dispatch-note\">",
                                  48 to "        {new Date().getHours() < 14 ? \"Order now for same-day dispatch\" : \"Ships tomorrow\"}",
                                  49 to "      </p>",
                                ),
                            ),
                            DemoFrame("throwOnHydrationMismatch", filename = REACT_DOM_JS, lineNo = 4_316, colNo = 9, inApp = false),
                          ),
                    ),
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(
                      4,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/session/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(
                      3,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(
                      3,
                      "fetch",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/canvas-tote/stock/",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(1, "ui.click", "button.size-option[data-size=\"M\"]"),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/products/canvas-tote"),
                contexts = SAFARI_MAC_CONTEXTS,
                userIndex = 29,
              ),
          ),
        firstSeenAgo = Duration.ofDays(45),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-fetch-failed",
            projectSlug = SHOP_WEB,
            title = "TypeError: Failed to fetch",
            culprit = "src/api/client.ts in request",
            level = "error",
            isUnhandled = false,
            ratePerHour = 12.0,
            userShare = 0.25,
            // Chrome's wording for a request that never got an answer: shoppers on flaky connections, retrying.
            tags =
              webTags(
                url =
                  listOf(
                    "https://shop.example.com/cart" to 0.45,
                    "https://shop.example.com/checkout" to 0.3,
                    "https://shop.example.com/search" to 0.25,
                  ),
                browser = listOf("Chrome 131" to 0.55, "Chrome Mobile 131" to 0.3, "Edge 131" to 0.15),
                os = listOf("Windows 11" to 0.5, "Android 15" to 0.3, "macOS 15" to 0.2),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "TypeError",
                      value = "Failed to fetch",
                      frames =
                        listOf(
                          DemoFrame("updateQuantity", filename = "src/features/cart/useCart.ts", lineNo = 57, colNo = 11),
                          DemoFrame("updateLine", filename = "src/api/cart.ts", lineNo = 24, colNo = 10),
                          DemoFrame(
                            "request",
                            filename = "src/api/client.ts",
                            lineNo = 41,
                            colNo = 26,
                            context =
                              listOf(
                                40 to "export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {",
                                41 to "  const response = await fetch(`\${API_BASE}\${path}`, { ...init, headers: authHeaders(init.headers) });",
                                42 to "  if (!response.ok) throw new ApiError(response.status, await response.text());",
                              ),
                          ),
                        ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(64, "navigation", type = "navigation", data = mapOf("from" to "/products/merino-beanie", "to" to "/cart")),
                    DemoBreadcrumb(
                      63,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(12, "ui.click", "button.quantity-increase"),
                    DemoBreadcrumb(
                      11,
                      "fetch",
                      level = "error",
                      type = "http",
                      data = mapOf("method" to "PATCH", "url" to "https://api.shop.example.com/api/v2/cart/lines/91/"),
                    ),
                    DemoBreadcrumb(10, "console", "[api] PATCH /api/v2/cart/lines/91/ failed; retrying once", level = "warning"),
                    DemoBreadcrumb(
                      3,
                      "fetch",
                      level = "error",
                      type = "http",
                      data = mapOf("method" to "PATCH", "url" to "https://api.shop.example.com/api/v2/cart/lines/91/"),
                    ),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/cart"),
                contexts = WEB_CONTEXTS,
                userIndex = 66,
              ),
          ),
        firstSeenAgo = Duration.ofDays(60),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-resize-observer",
            projectSlug = SHOP_WEB,
            title = "ResizeObserver loop completed with undelivered notifications.",
            culprit = "src/hooks/useElementSize.ts in observe",
            level = "info",
            isUnhandled = false,
            ratePerHour = 25.0,
            userShare = 0.4,
            // Harmless: the browser only puts off some resize notifications to the next frame. Archived as noise.
            tags =
              webTags(
                url =
                  listOf(
                    "https://shop.example.com/products/canvas-tote" to 0.4,
                    "https://shop.example.com/products/linen-shirt" to 0.3,
                    "https://shop.example.com/" to 0.3,
                  ),
                browser = MAC_BROWSERS,
                os = MAC_OSES,
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "Error",
                      value = "ResizeObserver loop completed with undelivered notifications.",
                      frames =
                        REACT_EFFECTS +
                          listOf(
                            DemoFrame("useElementSize", filename = "src/hooks/useElementSize.ts", lineNo = 14, colNo = 12),
                            DemoFrame(
                              "observe",
                              filename = "src/hooks/useElementSize.ts",
                              lineNo = 31,
                              colNo = 12,
                              context =
                                listOf(
                                  30 to "  const observer = new ResizeObserver(([entry]) => onResize(entry.contentRect));",
                                  31 to "  observer.observe(element);",
                                  32 to "  return () => observer.disconnect();",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(
                      40,
                      "navigation",
                      type = "navigation",
                      data = mapOf("from" to "/search", "to" to "/products/canvas-tote"),
                    ),
                    DemoBreadcrumb(
                      39,
                      "fetch",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/canvas-tote/",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(12, "ui.click", "div.product-gallery button.thumbnail:nth-child(3)"),
                    DemoBreadcrumb(4, "ui.click", "button.size-guide-toggle"),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/products/canvas-tote"),
                contexts = SAFARI_MAC_CONTEXTS,
                userIndex = 150,
              ),
          ),
        firstSeenAgo = Duration.ofDays(55),
        start = DemoStart.Archived(Duration.ofDays(9)),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-card-declined",
            projectSlug = SHOP_WEB,
            title = "Payment declined: card_declined",
            culprit = "checkout/payment",
            level = "warning",
            isUnhandled = false,
            ratePerHour = 2.0,
            userShare = 0.85,
            tags = webTags(url = listOf("https://shop.example.com/checkout" to 1.0), browser = PHONE_BROWSERS, os = PHONE_OSES),
            event =
              DemoEventSpec(
                message = "Payment declined: card_declined (order total 84.90 USD)",
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(95, "navigation", type = "navigation", data = mapOf("from" to "/cart", "to" to "/checkout")),
                    DemoBreadcrumb(
                      94,
                      "fetch",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/checkout/shipping-options/",
                          "status_code" to "200",
                        ),
                    ),
                    DemoBreadcrumb(7, "ui.click", "form#payment-form button[type=\"submit\"]"),
                    DemoBreadcrumb(
                      6,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/orders/", "status_code" to "201"),
                    ),
                    DemoBreadcrumb(
                      2,
                      "fetch",
                      level = "warning",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "POST",
                          "url" to "https://api.shop.example.com/api/v2/orders/48377/pay/",
                          "status_code" to "402",
                        ),
                    ),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/checkout"),
                contexts = SAFARI_IPHONE_CONTEXTS,
                userIndex = 81,
              ),
          ),
        firstSeenAgo = Duration.ofDays(20),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-storage-quota",
            projectSlug = SHOP_WEB,
            title =
              "QuotaExceededError: Failed to execute 'setItem' on 'Storage': " +
                "Setting the value of 'recentlyViewed' exceeded the quota.",
            culprit = "src/utils/storage.ts in saveRecentlyViewed",
            level = "error",
            isUnhandled = false,
            ratePerHour = 0.5,
            userShare = 0.95,
            // Shoppers who look at many products: the list is never trimmed.
            tags =
              webTags(
                url =
                  listOf(
                    "https://shop.example.com/products/merino-beanie" to 0.3,
                    "https://shop.example.com/products/linen-shirt" to 0.25,
                    "https://shop.example.com/products/canvas-tote" to 0.25,
                    "https://shop.example.com/products/suede-chelsea-boot" to 0.2,
                  ),
                browser = listOf("Chrome 131" to 0.65, "Edge 131" to 0.35),
                os = listOf("Windows 11" to 0.6, "macOS 15" to 0.25, "Android 15" to 0.15),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "QuotaExceededError",
                      value = "Failed to execute 'setItem' on 'Storage': Setting the value of 'recentlyViewed' exceeded the quota.",
                      frames =
                        REACT_EFFECTS +
                          listOf(
                            DemoFrame(
                              "useRecentlyViewed",
                              filename = "src/features/products/useRecentlyViewed.ts",
                              lineNo = 19,
                              colNo = 7,
                            ),
                            DemoFrame(
                              "saveRecentlyViewed",
                              filename = "src/utils/storage.ts",
                              lineNo = 37,
                              colNo = 16,
                              context =
                                listOf(
                                  36 to "  const next = [product, ...readRecentlyViewed().filter((item) => item.id !== product.id)];",
                                  37 to "  localStorage.setItem(\"recentlyViewed\", JSON.stringify(next));",
                                  38 to "}",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(
                      420,
                      "navigation",
                      type = "navigation",
                      data = mapOf("from" to "/search", "to" to "/products/linen-shirt"),
                    ),
                    DemoBreadcrumb(
                      260,
                      "navigation",
                      type = "navigation",
                      data = mapOf("from" to "/products/linen-shirt", "to" to "/products/canvas-tote"),
                    ),
                    DemoBreadcrumb(
                      130,
                      "navigation",
                      type = "navigation",
                      data = mapOf("from" to "/products/canvas-tote", "to" to "/products/suede-chelsea-boot"),
                    ),
                    DemoBreadcrumb(
                      3,
                      "navigation",
                      type = "navigation",
                      data = mapOf("from" to "/products/suede-chelsea-boot", "to" to "/products/merino-beanie"),
                    ),
                    DemoBreadcrumb(
                      2,
                      "fetch",
                      type = "http",
                      data =
                        mapOf(
                          "method" to "GET",
                          "url" to "https://api.shop.example.com/api/v2/products/merino-beanie/",
                          "status_code" to "200",
                        ),
                    ),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/products/merino-beanie"),
                contexts = WEB_CONTEXTS,
                userIndex = 197,
              ),
          ),
        firstSeenAgo = Duration.ofHours(70),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-coupon-tofixed",
            projectSlug = SHOP_WEB,
            title = "TypeError: coupon.discount.toFixed is not a function",
            culprit = "src/components/CouponBadge.tsx in CouponBadge",
            level = "error",
            isUnhandled = true,
            ratePerHour = 0.7,
            userShare = 0.8,
            tags =
              webTags(
                url = listOf("https://shop.example.com/cart" to 0.7, "https://shop.example.com/checkout" to 0.3),
                browser = listOf("Firefox 133" to 0.4, "Chrome 131" to 0.35, "Safari 18.1" to 0.15, "Edge 131" to 0.1),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    reactComponentStack(
                      "TypeError",
                      "coupon.discount.toFixed is not a function",
                      DemoFrame("CartPage", filename = "src/pages/cart.tsx", lineNo = 16, colNo = 5),
                      DemoFrame("CartSummary", filename = "src/components/CartSummary.tsx", lineNo = 37, colNo = 9),
                      DemoFrame("CouponBadge", filename = "src/components/CouponBadge.tsx", lineNo = 11, colNo = 8),
                    ),
                    DemoException(
                      type = "TypeError",
                      value = "coupon.discount.toFixed is not a function",
                      frames =
                        REACT_RENDER +
                          // Amount-off coupons come from the API with the amount as a string ("15.00").
                          DemoFrame(
                            "CouponBadge",
                            filename = "src/components/CouponBadge.tsx",
                            lineNo = 12,
                            colNo = 34,
                            context =
                              listOf(
                                11 to "export function CouponBadge({ coupon }: { coupon: Coupon }) {",
                                12 to "  const amount = coupon.discount.toFixed(2);",
                                13 to "  return <span className=\"coupon-badge\">-{amount} {coupon.currency}</span>;",
                              ),
                          ),
                    ),
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(33, "navigation", type = "navigation", data = mapOf("from" to "/products/linen-shirt", "to" to "/cart")),
                    DemoBreadcrumb(
                      32,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(9, "ui.click", "form.coupon-form button[type=\"submit\"]"),
                    DemoBreadcrumb(
                      8,
                      "fetch",
                      type = "http",
                      data =
                        mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/cart/coupons/", "status_code" to "201"),
                    ),
                    DemoBreadcrumb(
                      2,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/cart/", "status_code" to "200"),
                    ),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/cart"),
                contexts = FIREFOX_WINDOWS_CONTEXTS,
                userIndex = 135,
              ),
          ),
        firstSeenAgo = Duration.ofDays(10),
        start = DemoStart.Regressed(Duration.ofHours(20)),
        assigneeId = "1002",
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "web-search-abort",
            projectSlug = SHOP_WEB,
            title = "AbortError: The user aborted a request.",
            culprit = "src/features/search/useSearch.ts in fetchResults",
            level = "info",
            isUnhandled = false,
            ratePerHour = 6.0,
            userShare = 0.3,
            // Typing cancels the previous search's request; resolved by no longer reporting the cancelled ones.
            tags =
              webTags(
                url = listOf("https://shop.example.com/search" to 0.8, "https://shop.example.com/" to 0.2),
                browser = listOf("Chrome 131" to 0.7, "Edge 131" to 0.2, "Chrome Mobile 131" to 0.1),
              ),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "AbortError",
                      value = "The user aborted a request.",
                      frames =
                        listOf(
                          DemoFrame("handleChange", filename = "src/features/search/SearchBox.tsx", lineNo = 22, colNo = 5),
                          DemoFrame("search", filename = "src/features/search/useSearch.ts", lineNo = 41, colNo = 12),
                          DemoFrame(
                            "fetchResults",
                            filename = "src/features/search/useSearch.ts",
                            lineNo = 27,
                            colNo = 28,
                            context =
                              listOf(
                                26 to "  async function fetchResults(query: string, signal: AbortSignal) {",
                                27 to "    const response = await fetch(`\${SEARCH_URL}?q=\${encodeURIComponent(query)}`, { signal });",
                                28 to "    return (await response.json()) as SearchResults;",
                              ),
                          ),
                        ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(20, "navigation", type = "navigation", data = mapOf("from" to "/", "to" to "/search")),
                    DemoBreadcrumb(9, "ui.input", "input#search-query"),
                    DemoBreadcrumb(
                      8,
                      "fetch",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/search/?q=lin", "status_code" to "200"),
                    ),
                    DemoBreadcrumb(3, "ui.input", "input#search-query"),
                    DemoBreadcrumb(
                      1,
                      "fetch",
                      level = "error",
                      type = "http",
                      data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/search/?q=line"),
                    ),
                  ),
                request = DemoRequest("GET", "https://shop.example.com/search"),
                contexts = WEB_CONTEXTS,
                userIndex = 47,
              ),
          ),
        firstSeenAgo = Duration.ofDays(38),
        start = DemoStart.Resolved(Duration.ofDays(9)),
      ),
    )
}
