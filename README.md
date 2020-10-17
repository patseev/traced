## Logging + tracing pattern using middleware

This small project is an attempt to create a "framework"
for tracing and/or logging important parts of the application.

This solution strives to be simple, and almost mechanical. It can be injected 
into an existing code base gradually.

In this project I've implemented a simple `AuthService`, that depends on `UserRepository` and `EmailService` to register
new users and send out confirmation emails. Each entity has the same structure when it comes down to logging and tracing.

Everything is connected together in wiring and `AuthService` methods are exposed as `http4s` routes. 
Whole http4s app is wrapped in http4s-middleware, which is responsible for extraction of remote span id from headers,
or if span id is not provided, it creates a new root span that is being passed down to AuthService, UserRepository and
EmailService.

### The sauce

The solution is based around a very simple middleware typeclass `Mid`:

```scala
trait Mid[F[_], A] {
    def apply(fa: F[A]): F[A]
    def compose(that: Mid[F, A]): Mid[F, A]
}
```

If `F` is a `FlatMap`, we can do something before and/or after the `F[A]`, and this makes it useful for logging/tracing/caching/metrics. 
And we can also easily combine multiple middleware instances (it has a monoid instance as well).

Here's a complete example for logging and tracing an `EmailService`

```scala
@autoApplyK // cats-tagless auto-derivation of ApplyK instance for this algebra
trait EmailService[F[_]] {
  def sendRegistrationConfirmation(user: User): F[Unit]
  def sendWelcome(user: User): F[Unit]
}

object EmailService {

  def create[F[_]: Monad: Trace: Logger]: EmailService[F] = {
    val service = new Impl[F]
    /** Combining the middlewares via higher-kinded semigroup */
    (Traces[F] |+| Logs[F]).attach(service)
    /** middleware's attach method depends on ApplyK instance for Algebra, 
      thus the annotation above the EmailService trait*/
  }

  /** Dummy implementation */
  private class Impl[F[_]: Applicative] extends EmailService[F] {
    def sendRegistrationConfirmation(user: User): F[Unit] = Applicative[F].unit

    def sendWelcome(user: User): F[Unit] = Applicative[F].unit
  }

  private object Traces {
    /** Creates the span then executes the method */
    def apply[F[_]: Trace]: EmailService[Mid[F, *]] = 
      new EmailService[Mid[F, *]] {
        def sendRegistrationConfirmation(user: User): Mid[F, Unit] =
          Trace[F].span(s"Sending registration email to user ${user.id}")(_)

        def sendWelcome(user: User): Mid[F, Unit] =
          Trace[F].span(s"Sending welcome email to user ${user.id}")(_)
      }
  }

  private object Logs {
    /** Here we can log the invocation of the method with input, 
        then execute the method, and then log the result if we need to do that */
    def apply[F[_]: FlatMap](implicit logger: Logger[F]): EmailService[Mid[F, *]] = 
      new EmailService[Mid[F, *]] {
        def sendRegistrationConfirmation(user: User): Mid[F, Unit] =
          action =>
            logger.info(s"Sending confirmation email to ${user.id}") *> action <* logger.info(
              s"Confirmation email is sent to ${user.id}"
            )

        def sendWelcome(user: User): Mid[F, Unit] =
          action =>
            logger.info(s"Sending a welcome email to ${user.id}") *> action <* logger.info(
              s"Welcome emails is sent to ${user.id}"
            )
        
        /** What if we don't want to log anything for a method? 
            Since middleware is just a F[A] => F[A], we can use identity */
        def sendWelcome(user: User): Mid[F, Unit] = identity
      }
  }
}
```

### Explanation

In this example tracing is done via `Trace` typeclass from [natchez library](https://github.com/tpolecat/natchez). 
But it's very similar to other TF-based tracing solutions.

When `Trace[F]` is in scope, you can create new spans and put arbitrary data into it via `put` method. 
Generally, in order to create a span, you need to have access either to the entry point of the trace, or to the parent span.
This means that `F[_]: Trace` constraint makes us use something like `Kleisli[F, Span[F], *]` in run-time. 
You need to keep that in mind because this affects the wiring.

In this app, run-time effect of http layer is `Task`, while run-time effect of services is `Kleisli[Task, Span[Task], *]`.
Http layer has access to the method that creates new traces (or continues the old ones, passed via http headers), and then
passes down this trace to the services, that execute and create new spans in the trace.

### Output example

Here's a trace that is being created when client does `POST /register`:

```
{
  "name" : "http.request:/register",
  "service" : "App",
  "timestamp" : "2020-10-17T21:01:23.410903Z",
  "duration_ms" : 73,
  "trace.span_id" : "abddffbe-30e3-4a5f-9eeb-a862ff87c7e3",
  "trace.parent_id" : null,
  "trace.trace_id" : "d5c3fe04-2327-4d91-b457-301e7fb400f2",
  "exit.case" : "completed",
  "http.request.headers" : "User-Agent: PostmanRuntime/7.26.2\nAccept: */*\nPostman-Token: c52dfc1b-4648-4bda-829c-b1ccbf84770c\nHost: localhost:8080\nAccept-Encoding: gzip, deflate, br\nConnection: keep-alive\nContent-Length: 0\n",
  "http.method" : "POST",
  "span.type" : "web",
  "http.status_code" : 200,
  "http.response.headers" : "Content-Length: 0\n",
  "http.url" : "/register",
  "children" : [
    {
      "name" : "Registering a user email",
      "service" : "App",
      "timestamp" : "2020-10-17T21:01:23.441490Z",
      "duration_ms" : 39,
      "trace.span_id" : "27f6769e-1c24-49af-9753-21353516756c",
      "trace.parent_id" : "d5c3fe04-2327-4d91-b457-301e7fb400f2",
      "trace.trace_id" : "d5c3fe04-2327-4d91-b457-301e7fb400f2",
      "exit.case" : "completed",
      "children" : [
        {
          "name" : "Creating user with email email",
          "service" : "App",
          "timestamp" : "2020-10-17T21:01:23.442223Z",
          "duration_ms" : 4,
          "trace.span_id" : "2ed6afee-6da5-4ba2-9a2d-e4410c311741",
          "trace.parent_id" : "d5c3fe04-2327-4d91-b457-301e7fb400f2",
          "trace.trace_id" : "d5c3fe04-2327-4d91-b457-301e7fb400f2",
          "exit.case" : "completed",
          "children" : [
          ]
        },
        {
          "name" : "Sending registration email to user 997e67fb-0b3f-493a-8da8-7f8bc7b3b06a",
          "service" : "App",
          "timestamp" : "2020-10-17T21:01:23.480023Z",
          "duration_ms" : 0,
          "trace.span_id" : "8ed07514-736b-4948-91af-7369e1c86831",
          "trace.parent_id" : "d5c3fe04-2327-4d91-b457-301e7fb400f2",
          "trace.trace_id" : "d5c3fe04-2327-4d91-b457-301e7fb400f2",
          "exit.case" : "completed",
          "children" : [
          ]
        }
      ]
    }
  ]
}
```






