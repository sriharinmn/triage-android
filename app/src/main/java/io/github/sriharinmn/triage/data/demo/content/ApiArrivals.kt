package io.github.sriharinmn.triage.data.demo.content

/** The checkout API's issues that turn up during the demo, in order. */
internal object ApiArrivals {
  val all: List<DemoIssueSpec> =
    listOf(
      DemoIssueSpec(
        key = "api-arr-welcome-email",
        projectSlug = SHOP_API,
        title = "AttributeError: 'NoneType' object has no attribute 'email'",
        culprit = "shop.accounts.signals in send_welcome_email",
        level = "error",
        isUnhandled = true,
        ratePerHour = 0.7,
        userShare = 0.9,
        tags = apiTags(transaction = listOf("/api/v2/auth/register/" to 1.0)),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "AttributeError",
                  value = "'NoneType' object has no attribute 'email'",
                  frames =
                    DRF_RERAISED +
                      listOf(
                        DemoFrame("RegisterView.post", "shop.accounts.views", "shop/accounts/views.py", 46),
                        DemoFrame("save", "rest_framework.serializers", "rest_framework/serializers.py", 208, inApp = false),
                        DemoFrame("RegisterSerializer.create", "shop.accounts.serializers", "shop/accounts/serializers.py", 61),
                        DemoFrame("save", "django.db.models.base", "django/db/models/base.py", 822, inApp = false),
                        DemoFrame("save_base", "django.db.models.base", "django/db/models/base.py", 924, inApp = false),
                        DemoFrame("send", "django.dispatch.dispatcher", "django/dispatch/dispatcher.py", 189, inApp = false),
                        // Sign-ups without a referral code have no referrer.
                        DemoFrame(
                          "send_welcome_email",
                          "shop.accounts.signals",
                          "shop/accounts/signals.py",
                          19,
                          context =
                            listOf(
                              18 to "    if created:",
                              19 to "        send_welcome.delay(instance.pk, referrer_email=instance.referred_by.email)",
                              20 to "",
                            ),
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(
                  3,
                  "query",
                  "SELECT 1 AS \"a\" FROM \"accounts_user\" WHERE UPPER(\"accounts_user\".\"email\"::text) = UPPER(%s) LIMIT 1",
                ),
                DemoBreadcrumb(2, "accounts", "Sign-up without a referral code"),
                DemoBreadcrumb(
                  1,
                  "query",
                  "INSERT INTO \"accounts_user\" (\"password\", \"email\", \"referred_by_id\", \"date_joined\") " +
                    "VALUES (%s, %s, %s, %s) RETURNING \"accounts_user\".\"id\"",
                ),
                DemoBreadcrumb(
                  1,
                  "query",
                  "INSERT INTO \"accounts_profile\" (\"user_id\", \"locale\") VALUES (%s, %s) RETURNING \"accounts_profile\".\"id\"",
                ),
              ),
            request = DemoRequest("POST", "https://api.shop.example.com/api/v2/auth/register/"),
            contexts = API_CONTEXTS,
            userIndex = 248,
          ),
      ),
      DemoIssueSpec(
        key = "api-arr-zero-percent",
        projectSlug = SHOP_API,
        title = "ZeroDivisionError: division by zero",
        culprit = "shop.promotions.pricing in apply_percentage",
        level = "error",
        isUnhandled = true,
        ratePerHour = 0.5,
        userShare = 0.8,
        tags = apiTags(transaction = listOf("/api/v2/cart/coupons/" to 1.0)),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "ZeroDivisionError",
                  value = "division by zero",
                  frames =
                    DRF_RERAISED +
                      listOf(
                        DemoFrame("CartCouponView.post", "shop.cart.views", "shop/cart/views.py", 88),
                        DemoFrame("apply_coupon", "shop.promotions.pricing", "shop/promotions/pricing.py", 17),
                        // A cart holding only a free gift has a subtotal of 0.
                        DemoFrame(
                          "apply_percentage",
                          "shop.promotions.pricing",
                          "shop/promotions/pricing.py",
                          31,
                          context =
                            listOf(
                              30 to "    for line in cart.lines:",
                              31 to "        share = line.subtotal_cents / cart.subtotal_cents",
                              32 to "        line.discount_cents = round(discount_cents * share)",
                            ),
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(3, "query", TOKEN_QUERY),
                DemoBreadcrumb(3, "query", "SELECT \"cart_cart\".* FROM \"cart_cart\" WHERE \"cart_cart\".\"user_id\" = %s LIMIT 21"),
                DemoBreadcrumb(
                  2,
                  "query",
                  "SELECT \"promotions_coupon\".* FROM \"promotions_coupon\" " +
                    "WHERE UPPER(\"promotions_coupon\".\"code\"::text) = UPPER(%s) LIMIT 21",
                ),
                DemoBreadcrumb(1, "promotions", "Applying WELCOME10 (10% off) to cart 78102: 1 line, subtotal 0.00 USD"),
              ),
            request = DemoRequest("POST", "https://api.shop.example.com/api/v2/cart/coupons/"),
            contexts = API_CONTEXTS,
            userIndex = 33,
          ),
      ),
      DemoIssueSpec(
        key = "api-arr-upload-denied",
        projectSlug = SHOP_API,
        title = "ClientError: An error occurred (AccessDenied) when calling the PutObject operation: Access Denied",
        culprit = "shop.media.storage in upload_product_image",
        level = "error",
        isUnhandled = false,
        ratePerHour = 0.3,
        userShare = 0.6,
        // The storage bucket's new access policy left out the API's role. Only staff upload product photos.
        tags = apiTags(transaction = listOf("/api/v2/products/{pk}/images/" to 1.0)),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "ClientError",
                  value = "An error occurred (AccessDenied) when calling the PutObject operation: Access Denied",
                  module = "botocore.exceptions",
                  frames =
                    DRF_VIEW +
                      listOf(
                        DemoFrame("ProductImageView.post", "shop.catalog.views", "shop/catalog/views.py", 141),
                        DemoFrame(
                          "upload_product_image",
                          "shop.media.storage",
                          "shop/media/storage.py",
                          52,
                          context =
                            listOf(
                              51 to "    key = f\"products/{product.pk}/{position}.jpg\"",
                              52 to "    client.put_object(Bucket=settings.MEDIA_BUCKET, Key=key, Body=image, ContentType=\"image/jpeg\")",
                              53 to "    return f\"{settings.MEDIA_URL}{key}\"",
                            ),
                        ),
                        DemoFrame("_api_call", "botocore.client", "botocore/client.py", 569, inApp = false),
                        DemoFrame("_make_api_call", "botocore.client", "botocore/client.py", 1023, inApp = false),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(6, "query", TOKEN_QUERY),
                DemoBreadcrumb(
                  6,
                  "query",
                  "SELECT \"catalog_product\".\"id\", \"catalog_product\".\"slug\" FROM \"catalog_product\" " +
                    "WHERE \"catalog_product\".\"id\" = %s LIMIT 21",
                ),
                DemoBreadcrumb(5, "media", "Uploading products/88213/4.jpg (1.8 MB)"),
                DemoBreadcrumb(
                  1,
                  "httplib",
                  level = "error",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "PUT",
                      "url" to "https://media.storage.example.com/products/88213/4.jpg",
                      "status_code" to "403",
                    ),
                ),
              ),
            request = DemoRequest("POST", "https://api.shop.example.com/api/v2/products/88213/images/"),
            contexts = API_CONTEXTS,
            userIndex = 5,
          ),
      ),
      DemoIssueSpec(
        key = "api-arr-tracking-json",
        projectSlug = SHOP_API,
        title = "JSONDecodeError: Expecting value: line 1 column 1 (char 0)",
        culprit = "shop.shipping.carriers in fetch_tracking",
        level = "warning",
        isUnhandled = false,
        ratePerHour = 1.2,
        userShare = 0.5,
        tags = apiTags(transaction = listOf("shop.shipping.tasks.refresh_tracking" to 1.0), serverName = API_WORKERS),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "JSONDecodeError",
                  value = "Expecting value: line 1 column 1 (char 0)",
                  module = "json.decoder",
                  frames =
                    listOf(
                      DemoFrame("json", "requests.models", "requests/models.py", 974, inApp = false),
                      DemoFrame("loads", "json", "json/__init__.py", 346, inApp = false),
                      DemoFrame("decode", "json.decoder", "json/decoder.py", 337, inApp = false),
                      DemoFrame("raw_decode", "json.decoder", "json/decoder.py", 355, inApp = false),
                    ),
                ),
                DemoException(
                  type = "JSONDecodeError",
                  value = "Expecting value: line 1 column 1 (char 0)",
                  module = "requests.exceptions",
                  frames =
                    CELERY_TASK +
                      listOf(
                        DemoFrame("refresh_tracking", "shop.shipping.tasks", "shop/shipping/tasks.py", 27),
                        // The carrier's tracking API answers with an HTML maintenance page, and a 200.
                        DemoFrame(
                          "fetch_tracking",
                          "shop.shipping.carriers",
                          "shop/shipping/carriers.py",
                          83,
                          context =
                            listOf(
                              82 to "    response = session.get(f\"{TRACKING_URL}/shipments/{tracking_number}/events\", timeout=10)",
                              83 to "    return response.json()[\"events\"]",
                              84 to "",
                            ),
                        ),
                        DemoFrame("json", "requests.models", "requests/models.py", 978, inApp = false),
                      ),
                ),
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(
                  5,
                  "query",
                  "SELECT \"shipping_shipment\".* FROM \"shipping_shipment\" WHERE \"shipping_shipment\".\"id\" = %s LIMIT 21",
                ),
                DemoBreadcrumb(5, "query", "SELECT \"orders_order\".* FROM \"orders_order\" WHERE \"orders_order\".\"id\" = %s LIMIT 21"),
                DemoBreadcrumb(4, "shipping", "Refreshing tracking for shipment 3391 (EXP3391204)"),
                DemoBreadcrumb(
                  3,
                  "httplib",
                  type = "http",
                  data =
                    mapOf(
                      "method" to "GET",
                      "url" to "https://tracking.parcels.example.com/v2/shipments/EXP3391204/events",
                      "status_code" to "200",
                    ),
                ),
              ),
            contexts = API_CONTEXTS,
            userIndex = 174,
          ),
      ),
      DemoIssueSpec(
        key = "api-arr-naive-datetime",
        projectSlug = SHOP_API,
        title = "TypeError: can't compare offset-naive and offset-aware datetimes",
        culprit = "shop.orders.scheduling in next_delivery_slot",
        level = "error",
        isUnhandled = true,
        ratePerHour = 0.6,
        userShare = 0.85,
        tags = apiTags(transaction = listOf("/api/v2/checkout/delivery-slots/" to 1.0)),
        event =
          DemoEventSpec(
            exceptions =
              listOf(
                DemoException(
                  type = "TypeError",
                  value = "can't compare offset-naive and offset-aware datetimes",
                  frames =
                    DRF_RERAISED +
                      listOf(
                        DemoFrame("DeliverySlotsView.get", "shop.checkout.views", "shop/checkout/views.py", 92),
                        // datetime.now() has no time zone; the slots from the database have one.
                        DemoFrame(
                          "next_delivery_slot",
                          "shop.orders.scheduling",
                          "shop/orders/scheduling.py",
                          28,
                          context =
                            listOf(
                              27 to "    for slot in DeliverySlot.objects.filter(region=region).order_by(\"starts_at\"):",
                              28 to "        if slot.starts_at > datetime.now() + ORDER_CUTOFF:",
                              29 to "            return slot",
                            ),
                        ),
                      ),
                )
              ),
            breadcrumbs =
              listOf(
                DemoBreadcrumb(3, "query", TOKEN_QUERY),
                DemoBreadcrumb(
                  3,
                  "query",
                  "SELECT \"checkout_session\".\"id\", \"checkout_session\".\"region\" FROM \"checkout_session\" " +
                    "WHERE \"checkout_session\".\"user_id\" = %s LIMIT 21",
                ),
                DemoBreadcrumb(2, "checkout", "Finding the next delivery slot in region north-west"),
                DemoBreadcrumb(
                  2,
                  "query",
                  "SELECT \"orders_deliveryslot\".\"id\", \"orders_deliveryslot\".\"starts_at\" FROM \"orders_deliveryslot\" " +
                    "WHERE \"orders_deliveryslot\".\"region\" = %s ORDER BY \"orders_deliveryslot\".\"starts_at\" ASC",
                ),
              ),
            request = DemoRequest("GET", "https://api.shop.example.com/api/v2/checkout/delivery-slots/"),
            contexts = API_CONTEXTS,
            userIndex = 211,
          ),
      ),
    )
}
