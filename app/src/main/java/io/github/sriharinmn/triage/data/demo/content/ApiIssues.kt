package io.github.sriharinmn.triage.data.demo.content

import java.time.Duration

// The checkout API's shared pieces (also used by ApiArrivals.kt), then its issues at the demo's start.

internal val API_TAGS: Map<String, List<Pair<String, Double>>> =
  mapOf(
    "environment" to listOf("production" to 0.8, "staging" to 0.2),
    "server_name" to listOf("api-1" to 0.4, "api-2" to 0.35, "api-3" to 0.25),
    "transaction" to listOf("/api/v2/orders/" to 0.7, "/api/v2/cart/" to 0.3),
  )

/**
 * [API_TAGS] for another endpoint or task. An event shows each tag's most common value, so the first [transaction] should
 * be the event's route (`{pk}` for a path part) or its Celery task.
 */
internal fun apiTags(
  transaction: List<Pair<String, Double>>,
  serverName: List<Pair<String, Double>> = API_TAGS.getValue("server_name"),
): Map<String, List<Pair<String, Double>>> =
  mapOf("environment" to API_TAGS.getValue("environment"), "server_name" to serverName, "transaction" to transaction)

/** The Celery workers, a task's `server_name`s. */
internal val API_WORKERS: List<Pair<String, Double>> = listOf("worker-1" to 0.55, "worker-2" to 0.45)

internal val API_CONTEXTS: Map<String, Map<String, String>> =
  mapOf(
    "runtime" to mapOf("name" to "CPython", "version" to "3.12.4"),
    "os" to mapOf("name" to "Linux", "version" to "6.1"),
  )

/** Django's request-handling frames under a view, oldest first. */
internal val DJANGO_REQUEST: List<DemoFrame> =
  listOf(
    DemoFrame("inner", "django.core.handlers.exception", "django/core/handlers/exception.py", 55, inApp = false),
    DemoFrame("_get_response", "django.core.handlers.base", "django/core/handlers/base.py", 197, inApp = false),
    DemoFrame("view", "django.views.generic.base", "django/views/generic/base.py", 104, inApp = false),
  )

/** Django REST framework's view calling its handler, under [DJANGO_REQUEST]. */
internal val DRF_VIEW: List<DemoFrame> =
  DJANGO_REQUEST + DemoFrame("dispatch", "rest_framework.views", "rest_framework/views.py", 506, inApp = false)

/**
 * [DRF_VIEW] for an exception DRF doesn't turn into a response. It re-raises the exception, so the traceback goes through
 * `dispatch` twice.
 */
internal val DRF_RERAISED: List<DemoFrame> =
  DJANGO_REQUEST +
    listOf(
      DemoFrame("dispatch", "rest_framework.views", "rest_framework/views.py", 509, inApp = false),
      DemoFrame("handle_exception", "rest_framework.views", "rest_framework/views.py", 469, inApp = false),
      DemoFrame("raise_uncaught_exception", "rest_framework.views", "rest_framework/views.py", 480, inApp = false),
      DemoFrame("dispatch", "rest_framework.views", "rest_framework/views.py", 506, inApp = false),
    )

/** Django running SQL through psycopg, down to the database's error that Django re-raises as its own. */
internal val DJANGO_SQL: List<DemoFrame> =
  listOf(
    DemoFrame("execute", "django.db.backends.utils", "django/db/backends/utils.py", 79, inApp = false),
    DemoFrame("_execute_with_wrappers", "django.db.backends.utils", "django/db/backends/utils.py", 92, inApp = false),
    DemoFrame("_execute", "django.db.backends.utils", "django/db/backends/utils.py", 100, inApp = false),
    DemoFrame("__exit__", "django.db.utils", "django/db/utils.py", 91, inApp = false),
    DemoFrame("_execute", "django.db.backends.utils", "django/db/backends/utils.py", 105, inApp = false),
    DemoFrame("execute", "psycopg.cursor", "psycopg/cursor.py", 97, inApp = false),
  )

/** A Celery worker running a task, oldest first. */
internal val CELERY_TASK: List<DemoFrame> =
  listOf(
    DemoFrame("trace_task", "celery.app.trace", "celery/app/trace.py", 453, inApp = false),
    DemoFrame("__protected_call__", "celery.app.trace", "celery/app/trace.py", 736, inApp = false),
  )

/** The token lookup Django REST framework runs first for a signed-in request. */
internal const val TOKEN_QUERY =
  "SELECT \"authtoken_token\".\"key\", \"accounts_user\".\"id\" FROM \"authtoken_token\" " +
    "INNER JOIN \"accounts_user\" ON (\"authtoken_token\".\"user_id\" = \"accounts_user\".\"id\") " +
    "WHERE \"authtoken_token\".\"key\" = %s LIMIT 21"

internal object ApiIssues {
  val starting: List<DemoStartingIssue> =
    listOf(
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-payment-timeout",
            projectSlug = SHOP_API,
            title = "PaymentProviderTimeout: Payment provider did not answer within 10s",
            culprit = "shop.payments.gateway in charge",
            level = "error",
            isUnhandled = false,
            ratePerHour = 2.2,
            userShare = 0.8,
            tags = API_TAGS,
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "ReadTimeout",
                      value = "HTTPSConnectionPool(host='api.payments.example.com', port=443): Read timed out. (read timeout=10)",
                      module = "requests.exceptions",
                      frames =
                        listOf(
                          DemoFrame("post", "shop.payments.client", "shop/payments/client.py", 61),
                          DemoFrame("send", "requests.adapters", "requests/adapters.py", 713, inApp = false),
                        ),
                    ),
                    DemoException(
                      type = "PaymentProviderTimeout",
                      value = "Payment provider did not answer within 10s",
                      module = "shop.payments.errors",
                      frames =
                        DJANGO_REQUEST +
                          listOf(
                            DemoFrame("create", "shop.orders.views", "shop/orders/views.py", 88),
                            DemoFrame("pay_for_order", "shop.orders.services", "shop/orders/services.py", 142),
                            DemoFrame(
                              "charge",
                              "shop.payments.gateway",
                              "shop/payments/gateway.py",
                              57,
                              context =
                                listOf(
                                  56 to "    except requests.ReadTimeout as error:",
                                  57 to "        raise PaymentProviderTimeout(f\"Payment provider did not answer within {TIMEOUT}s\") from error",
                                  58 to "",
                                ),
                            ),
                          ),
                    ),
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(11, "query", "SELECT \"orders_order\".\"id\", \"orders_order\".\"total\" FROM \"orders_order\" WHERE \"orders_order\".\"id\" = %s"),
                    DemoBreadcrumb(11, "query", "SELECT \"cart_line\".* FROM \"cart_line\" WHERE \"cart_line\".\"cart_id\" = %s"),
                    DemoBreadcrumb(10, "payments", "Charging order 48213 (84.90 USD)"),
                    DemoBreadcrumb(
                      10,
                      "httplib",
                      type = "http",
                      data = mapOf("method" to "POST", "url" to "https://api.payments.example.com/v1/charges"),
                    ),
                    DemoBreadcrumb(0, "payments", "No answer after 10s; giving up", level = "warning"),
                  ),
                request = DemoRequest("POST", "https://api.shop.example.com/api/v2/orders/"),
                contexts = API_CONTEXTS,
                userIndex = 108,
              ),
          ),
        firstSeenAgo = Duration.ofDays(14),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-inventory-duplicate",
            projectSlug = SHOP_API,
            title = "IntegrityError: duplicate key value violates unique constraint \"inventory_reservation_order_id_key\"",
            culprit = "shop.inventory.services in reserve_stock",
            level = "error",
            isUnhandled = true,
            ratePerHour = 1.8,
            userShare = 0.5,
            tags = apiTags(transaction = listOf("/api/v2/orders/{pk}/confirm/" to 1.0)),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "IntegrityError",
                      value =
                        "duplicate key value violates unique constraint \"inventory_reservation_order_id_key\"\n" +
                          "DETAIL:  Key (order_id)=(48219) already exists.",
                      module = "django.db.utils",
                      frames =
                        DRF_RERAISED +
                          listOf(
                            DemoFrame("confirm", "shop.orders.views", "shop/orders/views.py", 131),
                            // Two confirms of one order (a double tap, or a client's retry) both pass the check.
                            DemoFrame(
                              "reserve_stock",
                              "shop.inventory.services",
                              "shop/inventory/services.py",
                              72,
                              context =
                                listOf(
                                  71 to "    if not Reservation.objects.filter(order=order).exists():",
                                  72 to "        Reservation.objects.create(order=order, expires_at=timezone.now() + HOLD_FOR)",
                                  73 to "    return order.reservation",
                                ),
                            ),
                            DemoFrame("create", "django.db.models.query", "django/db/models/query.py", 679, inApp = false),
                            DemoFrame("save", "django.db.models.base", "django/db/models/base.py", 822, inApp = false),
                            DemoFrame("_do_insert", "django.db.models.base", "django/db/models/base.py", 1105, inApp = false),
                            DemoFrame(
                              "execute_sql",
                              "django.db.models.sql.compiler",
                              "django/db/models/sql/compiler.py",
                              1823,
                              inApp = false,
                            ),
                          ) +
                          DJANGO_SQL,
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(3, "query", TOKEN_QUERY),
                    DemoBreadcrumb(
                      3,
                      "query",
                      "SELECT \"orders_order\".\"id\", \"orders_order\".\"status\" FROM \"orders_order\" " +
                        "WHERE \"orders_order\".\"id\" = %s LIMIT 21",
                    ),
                    DemoBreadcrumb(2, "inventory", "Reserving stock for order 48219"),
                    DemoBreadcrumb(
                      2,
                      "query",
                      "SELECT 1 AS \"a\" FROM \"inventory_reservation\" WHERE \"inventory_reservation\".\"order_id\" = %s LIMIT 1",
                    ),
                    DemoBreadcrumb(
                      1,
                      "query",
                      "INSERT INTO \"inventory_reservation\" (\"order_id\", \"expires_at\") VALUES (%s, %s) " +
                        "RETURNING \"inventory_reservation\".\"id\"",
                    ),
                  ),
                request = DemoRequest("POST", "https://api.shop.example.com/api/v2/orders/48219/confirm/"),
                contexts = API_CONTEXTS,
                userIndex = 95,
              ),
          ),
        firstSeenAgo = Duration.ofDays(24),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-postal-code",
            projectSlug = SHOP_API,
            title = "KeyError: 'postal_code'",
            culprit = "shop.shipping.rates in quote_for_address",
            level = "error",
            isUnhandled = true,
            ratePerHour = 0.9,
            userShare = 0.7,
            tags = apiTags(transaction = listOf("/api/v2/checkout/shipping-options/" to 1.0)),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "KeyError",
                      value = "'postal_code'",
                      frames =
                        DRF_RERAISED +
                          listOf(
                            DemoFrame("ShippingOptionsView.get", "shop.checkout.views", "shop/checkout/views.py", 58),
                            // Addresses in places without postal codes (Hong Kong, the UAE) leave the field out.
                            DemoFrame(
                              "quote_for_address",
                              "shop.shipping.rates",
                              "shop/shipping/rates.py",
                              34,
                              context =
                                listOf(
                                  33 to "    address = session.shipping_address.as_dict()",
                                  34 to "    zone = ShippingZone.for_postcode(address[\"country\"], address[\"postal_code\"])",
                                  35 to "    return [rate.quote(session.cart, zone) for rate in zone.rates.active()]",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(4, "query", TOKEN_QUERY),
                    DemoBreadcrumb(
                      4,
                      "query",
                      "SELECT \"checkout_session\".\"id\", \"checkout_session\".\"cart_id\" FROM \"checkout_session\" " +
                        "WHERE \"checkout_session\".\"user_id\" = %s LIMIT 21",
                    ),
                    DemoBreadcrumb(
                      3,
                      "query",
                      "SELECT \"accounts_address\".* FROM \"accounts_address\" WHERE \"accounts_address\".\"id\" = %s LIMIT 21",
                    ),
                    DemoBreadcrumb(3, "shipping", "Quoting shipping for cart 77314 to HK"),
                  ),
                request = DemoRequest("GET", "https://api.shop.example.com/api/v2/checkout/shipping-options/"),
                contexts = API_CONTEXTS,
                userIndex = 233,
              ),
          ),
        firstSeenAgo = Duration.ofHours(9),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-email-retries",
            projectSlug = SHOP_API,
            title = "MaxRetriesExceededError: Can't retry shop.emails.tasks.send_order_confirmation[5f2c9e1a] args:(48213,) kwargs:{}",
            culprit = "shop.emails.tasks in send_order_confirmation",
            level = "error",
            isUnhandled = false,
            ratePerHour = 0.6,
            userShare = 0.9,
            // The mail provider's maintenance windows outlast the task's retries.
            tags = apiTags(transaction = listOf("shop.emails.tasks.send_order_confirmation" to 1.0), serverName = API_WORKERS),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "HTTPError",
                      value = "503 Server Error: Service Unavailable for url: https://api.mail.example.com/v1/messages",
                      module = "requests.exceptions",
                      frames =
                        listOf(
                          DemoFrame("send_order_confirmation", "shop.emails.tasks", "shop/emails/tasks.py", 41),
                          DemoFrame("send", "shop.emails.client", "shop/emails/client.py", 29),
                          DemoFrame("raise_for_status", "requests.models", "requests/models.py", 1024, inApp = false),
                        ),
                    ),
                    DemoException(
                      type = "MaxRetriesExceededError",
                      value = "Can't retry shop.emails.tasks.send_order_confirmation[5f2c9e1a] args:(48213,) kwargs:{}",
                      module = "celery.exceptions",
                      frames =
                        CELERY_TASK +
                          listOf(
                            DemoFrame(
                              "send_order_confirmation",
                              "shop.emails.tasks",
                              "shop/emails/tasks.py",
                              45,
                              context =
                                listOf(
                                  44 to "    except requests.HTTPError:",
                                  45 to "        raise self.retry(countdown=60 * 2 ** self.request.retries)",
                                  46 to "",
                                ),
                            ),
                            DemoFrame("retry", "celery.app.task", "celery/app/task.py", 736, inApp = false),
                          ),
                    ),
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(5, "query", "SELECT \"orders_order\".* FROM \"orders_order\" WHERE \"orders_order\".\"id\" = %s LIMIT 21"),
                    DemoBreadcrumb(
                      5,
                      "query",
                      "SELECT \"orders_orderline\".* FROM \"orders_orderline\" WHERE \"orders_orderline\".\"order_id\" = %s",
                    ),
                    DemoBreadcrumb(4, "emails", "Sending order_confirmation for order 48213 (attempt 6 of 6)"),
                    DemoBreadcrumb(
                      1,
                      "httplib",
                      level = "error",
                      type = "http",
                      data = mapOf("method" to "POST", "url" to "https://api.mail.example.com/v1/messages", "status_code" to "503"),
                    ),
                  ),
                contexts = API_CONTEXTS,
                userIndex = 14,
              ),
          ),
        firstSeenAgo = Duration.ofDays(35),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-statement-timeout",
            projectSlug = SHOP_API,
            title = "OperationalError: canceling statement due to statement timeout",
            culprit = "shop.catalog.views in ProductListView.get",
            level = "error",
            isUnhandled = true,
            ratePerHour = 0.4,
            userShare = 0.6,
            tags = apiTags(transaction = listOf("/api/v2/products/" to 1.0)),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "OperationalError",
                      value = "canceling statement due to statement timeout",
                      module = "django.db.utils",
                      frames =
                        DRF_RERAISED +
                          listOf(
                            DemoFrame("timed.<locals>.wrapper", "shop.core.metrics", "shop/core/metrics.py", 27),
                            // Counting a large category's matches for the page links runs past the 5 s limit.
                            DemoFrame(
                              "ProductListView.get",
                              "shop.catalog.views",
                              "shop/catalog/views.py",
                              53,
                              context =
                                listOf(
                                  52 to "        products = Product.objects.filter(filters).order_by(ordering).distinct()",
                                  53 to "        page = self.paginate_queryset(products)",
                                  54 to "        return self.get_paginated_response(ProductSerializer(page, many=True).data)",
                                ),
                            ),
                            DemoFrame("paginate_queryset", "rest_framework.generics", "rest_framework/generics.py", 175, inApp = false),
                            DemoFrame(
                              "paginate_queryset",
                              "rest_framework.pagination",
                              "rest_framework/pagination.py",
                              211,
                              inApp = false,
                            ),
                            DemoFrame("count", "django.core.paginator", "django/core/paginator.py", 98, inApp = false),
                            DemoFrame("count", "django.db.models.query", "django/db/models/query.py", 620, inApp = false),
                            DemoFrame("get_count", "django.db.models.sql.query", "django/db/models/sql/query.py", 627, inApp = false),
                            DemoFrame(
                              "get_aggregation",
                              "django.db.models.sql.query",
                              "django/db/models/sql/query.py",
                              613,
                              inApp = false,
                            ),
                            DemoFrame(
                              "execute_sql",
                              "django.db.models.sql.compiler",
                              "django/db/models/sql/compiler.py",
                              1574,
                              inApp = false,
                            ),
                          ) +
                          DJANGO_SQL,
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(6, "query", TOKEN_QUERY),
                    DemoBreadcrumb(
                      6,
                      "query",
                      "SELECT \"catalog_category\".\"id\", \"catalog_category\".\"slug\" FROM \"catalog_category\" " +
                        "WHERE \"catalog_category\".\"slug\" = %s LIMIT 21",
                    ),
                    DemoBreadcrumb(5, "catalog", "Listing products: category=outerwear, color=green, sort=-popularity"),
                    DemoBreadcrumb(
                      5,
                      "query",
                      "SELECT COUNT(*) FROM (SELECT DISTINCT \"catalog_product\".\"id\" AS \"col1\" FROM \"catalog_product\" " +
                        "INNER JOIN \"catalog_variant\" ON (\"catalog_product\".\"id\" = \"catalog_variant\".\"product_id\") " +
                        "WHERE (\"catalog_product\".\"category_id\" = %s AND \"catalog_variant\".\"color\" = %s)) subquery",
                    ),
                  ),
                request = DemoRequest("GET", "https://api.shop.example.com/api/v2/products/"),
                contexts = API_CONTEXTS,
                userIndex = 160,
              ),
          ),
        firstSeenAgo = Duration.ofDays(11),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-webhook-signature",
            projectSlug = SHOP_API,
            title = "SignatureVerificationError: No signatures found matching the expected signature for payload",
            culprit = "shop.payments.webhooks in payment_webhook",
            level = "warning",
            isUnhandled = false,
            ratePerHour = 1.0,
            userShare = 0.05,
            tags = apiTags(transaction = listOf("/api/v2/payments/webhook/" to 1.0)),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "SignatureVerificationError",
                      value = "No signatures found matching the expected signature for payload",
                      module = "shop.payments.errors",
                      // A function view, so no class-based `view` frame.
                      frames =
                        DJANGO_REQUEST.dropLast(1) +
                          listOf(
                            DemoFrame("_view_wrapper", "django.views.decorators.csrf", "django/views/decorators/csrf.py", 65, inApp = false),
                            DemoFrame("idempotent.<locals>.wrapper", "shop.core.idempotency", "shop/core/idempotency.py", 38),
                            // The payment provider also sends its test-mode events here, signed with the test secret.
                            DemoFrame(
                              "payment_webhook",
                              "shop.payments.webhooks",
                              "shop/payments/webhooks.py",
                              42,
                              context =
                                listOf(
                                  41 to "    if not signature.matches(request.body, secret=settings.PAYMENTS_WEBHOOK_SECRET):",
                                  42 to "        raise SignatureVerificationError(\"No signatures found matching the expected signature for payload\")",
                                  43 to "",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(
                      1,
                      "query",
                      "SELECT \"core_idempotencykey\".\"id\" FROM \"core_idempotencykey\" WHERE \"core_idempotencykey\".\"key\" = %s LIMIT 21",
                    ),
                    DemoBreadcrumb(
                      1,
                      "query",
                      "INSERT INTO \"core_idempotencykey\" (\"key\", \"created_at\") VALUES (%s, %s) " +
                        "RETURNING \"core_idempotencykey\".\"id\"",
                    ),
                    DemoBreadcrumb(0, "payments", "Webhook delivery 7731: charge.succeeded (test mode)"),
                    DemoBreadcrumb(0, "payments", "The signature header has 1 signature; none matches the live secret", level = "warning"),
                  ),
                request = DemoRequest("POST", "https://api.shop.example.com/api/v2/payments/webhook/"),
                contexts = API_CONTEXTS,
                // The payment provider calls this endpoint; there's no shopper.
                userIndex = null,
              ),
          ),
        firstSeenAgo = Duration.ofDays(42),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-redis-refused",
            projectSlug = SHOP_API,
            title = "ConnectionError: Error 111 connecting to redis:6379. Connection refused.",
            culprit = "shop.cart.cache in get_cart",
            level = "error",
            isUnhandled = true,
            ratePerHour = 0.25,
            userShare = 0.9,
            // Seconds-long gaps while Redis fails over; the cart has no fallback to the database.
            tags = apiTags(transaction = listOf("/api/v2/cart/" to 0.8, "/api/v2/cart/items/" to 0.2)),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "ConnectionRefusedError",
                      value = "[Errno 111] Connection refused",
                      frames =
                        listOf(
                          DemoFrame("connect", "redis.connection", "redis/connection.py", 353, inApp = false),
                          DemoFrame("call_with_retry", "redis.retry", "redis/retry.py", 62, inApp = false),
                          DemoFrame("<lambda>", "redis.connection", "redis/connection.py", 354, inApp = false),
                          DemoFrame("_connect", "redis.connection", "redis/connection.py", 717, inApp = false),
                          DemoFrame("create_connection", "socket", "socket.py", 852, inApp = false),
                        ),
                    ),
                    DemoException(
                      type = "ConnectionError",
                      value = "Error 111 connecting to redis:6379. Connection refused.",
                      module = "redis.exceptions",
                      frames =
                        DRF_RERAISED +
                          listOf(
                            DemoFrame("CartView.get", "shop.cart.views", "shop/cart/views.py", 37),
                            DemoFrame(
                              "get_cart",
                              "shop.cart.cache",
                              "shop/cart/cache.py",
                              24,
                              context =
                                listOf(
                                  23 to "def get_cart(cart_id: int) -> CartSnapshot | None:",
                                  24 to "    cached = redis_client.get(f\"cart:{cart_id}\")",
                                  25 to "    return CartSnapshot.parse(cached) if cached else None",
                                ),
                            ),
                            DemoFrame("get", "redis.commands.core", "redis/commands/core.py", 1829, inApp = false),
                            DemoFrame("execute_command", "redis.client", "redis/client.py", 559, inApp = false),
                            DemoFrame("_execute_command", "redis.client", "redis/client.py", 565, inApp = false),
                            DemoFrame("get_connection", "redis.connection", "redis/connection.py", 1422, inApp = false),
                            DemoFrame("connect", "redis.connection", "redis/connection.py", 359, inApp = false),
                          ),
                    ),
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(5, "query", TOKEN_QUERY),
                    DemoBreadcrumb(4, "query", "SELECT \"cart_cart\".\"id\" FROM \"cart_cart\" WHERE \"cart_cart\".\"user_id\" = %s LIMIT 21"),
                    DemoBreadcrumb(4, "cart", "Loading cart 77314 from the cache"),
                    DemoBreadcrumb(3, "redis", "GET 'cart:77314'", level = "error"),
                  ),
                request = DemoRequest("GET", "https://api.shop.example.com/api/v2/cart/"),
                contexts = API_CONTEXTS,
                userIndex = 77,
              ),
          ),
        firstSeenAgo = Duration.ofDays(22),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-decimal-total",
            projectSlug = SHOP_API,
            title = "InvalidOperation: [<class 'decimal.ConversionSyntax'>]",
            culprit = "shop.orders.serializers in OrderSerializer.validate_total",
            level = "error",
            isUnhandled = true,
            ratePerHour = 0.35,
            userShare = 0.8,
            tags = API_TAGS,
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "InvalidOperation",
                      value = "[<class 'decimal.ConversionSyntax'>]",
                      module = "decimal",
                      frames =
                        DJANGO_REQUEST +
                          listOf(
                            DemoFrame("create", "shop.orders.views", "shop/orders/views.py", 84),
                            DemoFrame("is_valid", "rest_framework.serializers", "rest_framework/serializers.py", 223, inApp = false),
                            DemoFrame("run_validation", "rest_framework.serializers", "rest_framework/serializers.py", 445, inApp = false),
                            DemoFrame("to_internal_value", "rest_framework.serializers", "rest_framework/serializers.py", 502, inApp = false),
                            // Clients set to a comma-decimal language send the total as "84,90".
                            DemoFrame(
                              "OrderSerializer.validate_total",
                              "shop.orders.serializers",
                              "shop/orders/serializers.py",
                              48,
                              context =
                                listOf(
                                  47 to "    def validate_total(self, value):",
                                  48 to "        total = Decimal(value).quantize(CENT)",
                                  49 to "        if total != self.cart_total():",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(3, "query", TOKEN_QUERY),
                    DemoBreadcrumb(
                      2,
                      "query",
                      "SELECT \"cart_cart\".\"id\", \"cart_cart\".\"currency\" FROM \"cart_cart\" WHERE \"cart_cart\".\"user_id\" = %s LIMIT 21",
                    ),
                    DemoBreadcrumb(2, "query", "SELECT \"cart_line\".* FROM \"cart_line\" WHERE \"cart_line\".\"cart_id\" = %s"),
                    DemoBreadcrumb(1, "orders", "Creating an order from cart 77921 (client total \"84,90\")"),
                  ),
                request = DemoRequest("POST", "https://api.shop.example.com/api/v2/orders/"),
                contexts = API_CONTEXTS,
                userIndex = 241,
              ),
          ),
        firstSeenAgo = Duration.ofDays(17),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-throttled",
            projectSlug = SHOP_API,
            title = "Throttled: Request was throttled. Expected available in 37 seconds.",
            culprit = "shop.api.throttling in check_rate",
            level = "warning",
            isUnhandled = false,
            ratePerHour = 9.0,
            userShare = 0.15,
            // A few clients that retry without waiting, mostly older Android builds syncing the cart. Archived as expected.
            tags = apiTags(transaction = listOf("/api/v2/cart/" to 0.75, "/api/v2/products/" to 0.25)),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "Throttled",
                      value = "Request was throttled. Expected available in 37 seconds.",
                      module = "rest_framework.exceptions",
                      frames =
                        DJANGO_REQUEST +
                          listOf(
                            DemoFrame("dispatch", "rest_framework.views", "rest_framework/views.py", 497, inApp = false),
                            DemoFrame("RateLimitedMixin.initial", "shop.api.mixins", "shop/api/mixins.py", 22),
                            DemoFrame(
                              "check_rate",
                              "shop.api.throttling",
                              "shop/api/throttling.py",
                              39,
                              context =
                                listOf(
                                  38 to "    if not bucket.consume(1):",
                                  39 to "        raise Throttled(wait=bucket.seconds_until_refill())",
                                  40 to "    return bucket",
                                ),
                            ),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(4, "query", TOKEN_QUERY),
                    DemoBreadcrumb(3, "redis", "GET 'ratelimit:cart:user-57'"),
                    DemoBreadcrumb(3, "redis", "TTL 'ratelimit:cart:user-57'"),
                    DemoBreadcrumb(2, "api", "Rate limit reached for user-57 on /api/v2/cart/: 60 requests a minute", level = "warning"),
                  ),
                request = DemoRequest("GET", "https://api.shop.example.com/api/v2/cart/"),
                contexts = API_CONTEXTS,
                userIndex = 57,
              ),
          ),
        firstSeenAgo = Duration.ofDays(47),
        start = DemoStart.Archived(Duration.ofDays(2)),
      ),
      DemoStartingIssue(
        spec =
          DemoIssueSpec(
            key = "api-invoice-rgba",
            projectSlug = SHOP_API,
            title = "OSError: cannot write mode RGBA as JPEG",
            culprit = "shop.invoices.render in render_logo",
            level = "error",
            isUnhandled = false,
            ratePerHour = 0.15,
            userShare = 0.9,
            tags = apiTags(transaction = listOf("/api/v2/orders/{pk}/invoice/" to 1.0)),
            event =
              DemoEventSpec(
                exceptions =
                  listOf(
                    DemoException(
                      type = "OSError",
                      value = "cannot write mode RGBA as JPEG",
                      frames =
                        DRF_VIEW +
                          listOf(
                            DemoFrame("InvoiceView.get", "shop.invoices.views", "shop/invoices/views.py", 29),
                            DemoFrame("render_invoice", "shop.invoices.render", "shop/invoices/render.py", 31),
                            // The new logo is a PNG with transparency, and JPEG has no alpha channel.
                            DemoFrame(
                              "render_logo",
                              "shop.invoices.render",
                              "shop/invoices/render.py",
                              62,
                              context =
                                listOf(
                                  61 to "    logo = Image.open(settings.INVOICE_LOGO_PATH)",
                                  62 to "    logo.save(buffer, format=\"JPEG\", quality=85)",
                                  63 to "    return buffer.getvalue()",
                                ),
                            ),
                            DemoFrame("save", "PIL.Image", "PIL/Image.py", 2568, inApp = false),
                            DemoFrame("_save", "PIL.JpegImagePlugin", "PIL/JpegImagePlugin.py", 639, inApp = false),
                          ),
                    )
                  ),
                breadcrumbs =
                  listOf(
                    DemoBreadcrumb(2, "query", TOKEN_QUERY),
                    DemoBreadcrumb(
                      2,
                      "query",
                      "SELECT \"orders_order\".* FROM \"orders_order\" " +
                        "WHERE (\"orders_order\".\"id\" = %s AND \"orders_order\".\"user_id\" = %s) LIMIT 21",
                    ),
                    DemoBreadcrumb(
                      1,
                      "query",
                      "SELECT \"orders_orderline\".* FROM \"orders_orderline\" WHERE \"orders_orderline\".\"order_id\" = %s",
                    ),
                    DemoBreadcrumb(1, "invoices", "Rendering invoice INV-2026-048230 for order 48230"),
                  ),
                request = DemoRequest("GET", "https://api.shop.example.com/api/v2/orders/48230/invoice/"),
                contexts = API_CONTEXTS,
                userIndex = 120,
              ),
          ),
        firstSeenAgo = Duration.ofDays(29),
      ),
    )
}
