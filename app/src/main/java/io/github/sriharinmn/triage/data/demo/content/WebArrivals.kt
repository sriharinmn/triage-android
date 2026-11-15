package io.github.sriharinmn.triage.data.demo.content

/** The web storefront's issues that turn up during the demo, in order. */
internal object WebArrivals {
  val all: List<DemoIssueSpec> =
    listOf(
      DemoIssueSpec(
        key = "web-arr-null-variant",
        projectSlug = SHOP_WEB,
        title = "TypeError: Cannot read properties of null (reading 'variantId')",
        culprit = "src/components/AddToCartButton.tsx in handleClick",
        level = "error",
        isUnhandled = true,
        ratePerHour = 1.0,
        userShare = 0.7,
        tags =
          webTags(
            url =
              listOf(
                "https://shop.example.com/products/linen-shirt" to 0.4,
                "https://shop.example.com/products/canvas-tote" to 0.3,
                "https://shop.example.com/products/merino-beanie" to 0.3,
              ),
          ),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "TypeError",
                  value = "Cannot read properties of null (reading 'variantId')",
                  frames =
                    REACT_EVENT +
                      listOf(
                        DemoFrame("tracked", filename = "src/analytics/trackClick.ts", lineNo = 14, colNo = 12),
                        // Picking another color clears the chosen size, but the button stays enabled.
                        DemoFrame(
                          "handleClick",
                          filename = "src/components/AddToCartButton.tsx",
                          lineNo = 34,
                          colNo = 42,
                          context =
                            listOf(
                              33 to "  function handleClick() {",
                              34 to "    addToCart({ variantId: selectedVariant.variantId, quantity });",
                              35 to "  }",
                            ),
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(41, "navigation", type = "navigation", data = mapOf("from" to "/search", "to" to "/products/linen-shirt")),
                DemoBreadcrumb(
                  40,
                  "fetch",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.shop.example.com/api/v2/products/linen-shirt/",
                      "status_code" to "200",
                    ),
                ),
                DemoBreadcrumb(15, "ui.click", "button.size-option[data-size=\"M\"]"),
                DemoBreadcrumb(7, "ui.click", "button.color-swatch[data-color=\"sage\"]"),
                DemoBreadcrumb(1, "ui.click", "button.add-to-cart"),
              ),
            request = DemoRequest("GET", "https://shop.example.com/products/linen-shirt"),
            contexts = WEB_CONTEXTS,
            userIndex = 71,
          ),
      ),
      DemoIssueSpec(
        key = "web-arr-locale",
        projectSlug = SHOP_WEB,
        title = "RangeError: Incorrect locale information provided",
        culprit = "src/utils/formatPrice.ts in formatPrice",
        level = "error",
        isUnhandled = true,
        ratePerHour = 0.4,
        userShare = 0.9,
        tags =
          webTags(
            url =
              listOf(
                "https://shop.example.com/cart" to 0.5,
                "https://shop.example.com/checkout" to 0.3,
                "https://shop.example.com/" to 0.2,
              ),
          ),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "RangeError",
                  value = "Incorrect locale information provided",
                  frames =
                    REACT_RENDER +
                      listOf(
                        DemoFrame("PriceTag", filename = "src/components/PriceTag.tsx", lineNo = 9, colNo = 18),
                        // The account's language now comes back from the API as "en_GB", a form Intl rejects.
                        DemoFrame(
                          "formatPrice",
                          filename = "src/utils/formatPrice.ts",
                          lineNo = 9,
                          colNo = 10,
                          context =
                            listOf(
                              8 to "export function formatPrice(amount: number, currency: string, locale = getAccountLocale()) {",
                              9 to "  return new Intl.NumberFormat(locale, { style: \"currency\", currency }).format(amount);",
                              10 to "}",
                            ),
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(30, "navigation", type = "navigation", data = mapOf("from" to "/account", "to" to "/account/preferences")),
                DemoBreadcrumb(12, "ui.click", "select#preferred-language"),
                DemoBreadcrumb(
                  9,
                  "fetch",
                  type = "http",
                  data = mapOf("method" to "PATCH", "url" to "https://api.shop.example.com/api/v2/me/", "status_code" to "200"),
                ),
                DemoBreadcrumb(
                  8,
                  "fetch",
                  type = "http",
                  data = mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/me/", "status_code" to "200"),
                ),
                DemoBreadcrumb(2, "navigation", type = "navigation", data = mapOf("from" to "/account/preferences", "to" to "/cart")),
              ),
            request = DemoRequest("GET", "https://shop.example.com/cart"),
            contexts = WEB_CONTEXTS,
            userIndex = 219,
          ),
      ),
      DemoIssueSpec(
        key = "web-arr-session-expired",
        projectSlug = SHOP_WEB,
        title = "Error: Checkout session expired",
        culprit = "src/features/checkout/useCheckoutSession.ts in refresh",
        level = "warning",
        isUnhandled = false,
        ratePerHour = 1.5,
        userShare = 0.6,
        // Shoppers coming back to a checkout they left open for more than 30 minutes, mostly on phones.
        tags = webTags(url = listOf("https://shop.example.com/checkout" to 1.0), browser = PHONE_BROWSERS, os = PHONE_OSES),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "Error",
                  value = "Checkout session expired",
                  frames =
                    listOf(
                      DemoFrame("onVisibilityChange", filename = "src/features/checkout/useCheckoutSession.ts", lineNo = 63, colNo = 7),
                      DemoFrame(
                        "refresh",
                        filename = "src/features/checkout/useCheckoutSession.ts",
                        lineNo = 51,
                        colNo = 41,
                        context =
                          listOf(
                            50 to "    const response = await api.get(`/checkout/sessions/\${sessionId}/`);",
                            51 to "    if (response.status === 410) throw new Error(\"Checkout session expired\");",
                            52 to "    setSession(await response.json());",
                          ),
                      ),
                    ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(2_400, "navigation", type = "navigation", data = mapOf("from" to "/cart", "to" to "/checkout")),
                DemoBreadcrumb(
                  2_399,
                  "fetch",
                  type = "http",
                  data =
                    mapOf("method" to "POST", "url" to "https://api.shop.example.com/api/v2/checkout/sessions/", "status_code" to "201"),
                ),
                DemoBreadcrumb(2_340, "ui.input", "input#email"),
                DemoBreadcrumb(2_331, "ui.click", "button.continue-to-shipping"),
                DemoBreadcrumb(
                  2,
                  "fetch",
                  level = "warning",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.shop.example.com/api/v2/checkout/sessions/30127/",
                      "status_code" to "410",
                    ),
                ),
              ),
            request = DemoRequest("GET", "https://shop.example.com/checkout"),
            contexts = SAFARI_IPHONE_CONTEXTS,
            userIndex = 58,
          ),
      ),
      DemoIssueSpec(
        key = "web-arr-image-decode",
        projectSlug = SHOP_WEB,
        title = "EncodingError: The source image cannot be decoded.",
        culprit = "src/components/ProductGallery.tsx in preload",
        level = "warning",
        isUnhandled = false,
        ratePerHour = 0.9,
        userShare = 0.5,
        // A few new product photos were uploaded as HEIC, which Safari decodes and Chrome-based browsers don't.
        tags =
          webTags(
            url =
              listOf(
                "https://shop.example.com/products/suede-chelsea-boot" to 0.6,
                "https://shop.example.com/products/canvas-tote" to 0.4,
              ),
            browser = listOf("Chrome 131" to 0.6, "Edge 131" to 0.25, "Chrome Mobile 131" to 0.15),
            os = listOf("Windows 11" to 0.55, "Android 15" to 0.25, "macOS 15" to 0.2),
          ),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "EncodingError",
                  value = "The source image cannot be decoded.",
                  frames =
                    REACT_EFFECTS +
                      listOf(
                        DemoFrame("ProductGallery", filename = "src/components/ProductGallery.tsx", lineNo = 34, colNo = 5),
                        DemoFrame(
                          "preload",
                          filename = "src/components/ProductGallery.tsx",
                          lineNo = 22,
                          colNo = 15,
                          context =
                            listOf(
                              21 to "  image.src = src;",
                              22 to "  await image.decode();",
                              23 to "  return image;",
                            ),
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(
                  22,
                  "navigation",
                  type = "navigation",
                  data = mapOf("from" to "/collections/new-arrivals", "to" to "/products/suede-chelsea-boot"),
                ),
                DemoBreadcrumb(
                  21,
                  "fetch",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.shop.example.com/api/v2/products/suede-chelsea-boot/",
                      "status_code" to "200",
                    ),
                ),
                DemoBreadcrumb(9, "ui.click", "div.product-gallery button.gallery-next"),
                DemoBreadcrumb(8, "ui.click", "div.product-gallery button.gallery-next"),
                DemoBreadcrumb(
                  2,
                  "console",
                  "Skipping a gallery image that failed to decode: /media/products/suede-chelsea-boot/4.heic",
                  level = "warning",
                ),
              ),
            request = DemoRequest("GET", "https://shop.example.com/products/suede-chelsea-boot"),
            contexts = WEB_CONTEXTS,
            userIndex = 183,
          ),
      ),
      DemoIssueSpec(
        key = "web-arr-update-depth",
        projectSlug = SHOP_WEB,
        title = "Error: Maximum update depth exceeded.",
        culprit = "src/features/filters/FilterPanel.tsx in FilterPanel",
        level = "error",
        isUnhandled = true,
        ratePerHour = 0.5,
        userShare = 0.8,
        tags = webTags(url = listOf("https://shop.example.com/search" to 1.0), browser = MAC_BROWSERS, os = MAC_OSES),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                reactComponentStack(
                  "Error",
                  "Maximum update depth exceeded.",
                  DemoFrame("SearchPage", filename = "src/pages/search.tsx", lineNo = 19, colNo = 5),
                  DemoFrame("FilterPanel", filename = "src/features/filters/FilterPanel.tsx", lineNo = 27, colNo = 8),
                ),
                DemoException(
                  type = "Error",
                  value = "Maximum update depth exceeded.",
                  frames =
                    listOf(
                      DemoFrame("commitRoot", filename = REACT_DOM_JS, lineNo = 15_160, colNo = 7, inApp = false),
                      DemoFrame("commitLayoutEffects", filename = REACT_DOM_JS, lineNo = 12_407, colNo = 7, inApp = false),
                      DemoFrame("commitHookEffectListMount", filename = REACT_DOM_JS, lineNo = 11_094, colNo = 29, inApp = false),
                      // Each update makes a new filters object, which runs the effect again.
                      DemoFrame(
                        "FilterPanel",
                        filename = "src/features/filters/FilterPanel.tsx",
                        lineNo = 41,
                        colNo = 5,
                        context =
                          listOf(
                            40 to "  useLayoutEffect(() => {",
                            41 to "    setFilters({ ...filters, price: clampPrice(filters.price, priceBounds) });",
                            42 to "  }, [filters, priceBounds]);",
                          ),
                      ),
                      DemoFrame("dispatchSetState", filename = REACT_DOM_JS, lineNo = 5_967, colNo = 5, inApp = false),
                      DemoFrame("scheduleUpdateOnFiber", filename = REACT_DOM_JS, lineNo = 13_528, colNo = 5, inApp = false),
                      DemoFrame("throwIfInfiniteUpdateLoopDetected", filename = REACT_DOM_JS, lineNo = 15_874, colNo = 11, inApp = false),
                    ),
                ),
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(35, "navigation", type = "navigation", data = mapOf("from" to "/", "to" to "/search")),
                DemoBreadcrumb(
                  34,
                  "fetch",
                  type = "http",
                  data =
                    mapOf("method" to "GET", "url" to "https://api.shop.example.com/api/v2/search/?q=wool+coat", "status_code" to "200"),
                ),
                DemoBreadcrumb(10, "ui.click", "button.filters-toggle"),
                DemoBreadcrumb(4, "ui.input", "input#price-max"),
                DemoBreadcrumb(
                  3,
                  "fetch",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://api.shop.example.com/api/v2/search/?q=wool+coat&price_max=120",
                      "status_code" to "200",
                    ),
                ),
              ),
            request = DemoRequest("GET", "https://shop.example.com/search"),
            contexts = SAFARI_MAC_CONTEXTS,
            userIndex = 126,
          ),
      ),
    )
}
