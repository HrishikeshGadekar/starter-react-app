import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestExecution;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import java.net.URI;
import java.util.Objects;

public class LoggingExchangeFilterFunction implements ExchangeFilterFunction {
    private final Logger log = LoggerFactory.getLogger(LoggingExchangeFilterFunction.class);

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (log.isTraceEnabled()) {
            log.trace("Request: {} {}{}", request.method(), request.url(), formatHeaders(request.headers()));
            request.body(BodyExtractors.toMono(String.class))
                    .doOnNext(reqBody -> log.trace("Request body: {}", reqBody))
                    .subscriberContext(ctx -> ctx.put("startTime", System.currentTimeMillis()))
                    .subscribe();
        } else if (log.isDebugEnabled()) {
            log.debug("Request: {} {}", request.method(), request.url());
        }
        return next.exchange(request)
                .doOnNext(clientResponse -> logResponse(request.method(), request.url(), clientResponse));
    }

    private void logResponse(HttpMethod method, URI uri, ClientResponse response) {
        long startTime = Objects.requireNonNull(response.attributes().get("startTime"));
        long elapsedTime = System.currentTimeMillis() - startTime;

        if (log.isTraceEnabled()) {
            log.trace("Response: {} {} ({}ms){}{}", method, uri, elapsedTime, formatHeaders(response.headers()), formatBody(response));
        } else if (log.isDebugEnabled()) {
            log.debug("Response: {} {} ({})", method, uri, elapsedTime);
        }
    }

    private String formatHeaders(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return "";
        }
        return headers.entrySet().stream()
                .map(entry -> "\n" + entry.getKey() + ": " + entry.getValue())
                .reduce("", (s1, s2) -> s1 + s2);
    }

    private String formatBody(ClientResponse response) {
        HttpStatus statusCode = response.statusCode();
        if (statusCode.is4xxClientError() || statusCode.is5xxServerError()) {
            return response.bodyToMono(String.class).map(body -> "\nResponse body: " + body).block();
        }
        return "";
    }
}




// 
// 
// 
WebClient webClient = WebClient.builder()
        .baseUrl("http://example.com")
        .filter(ExchangeFilterFunctions.ofRequestProcessor(request -> {
            log.trace("Request: {} {} {} {}",
                    request.method(),
                    request.url(),
                    request.headers(),
                    DataBufferUtils.join(request.body(BodyExtractors.toDataBuffers())).flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return Mono.just(new String(bytes, StandardCharsets.UTF_8));
                    }).orElse(""));
            return request;
        }))
        .filter((clientRequest, next) -> {
            long startTime = System.currentTimeMillis();

            return next.exchange(clientRequest)
                    .doOnSuccess(clientResponse -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.trace("Response: {} {} {} {} ({} ms)",
                                clientResponse.statusCode().value(),
                                HttpStatus.valueOf(clientResponse.statusCode().value()).getReasonPhrase(),
                                clientResponse.headers().asHttpHeaders(),
                                clientResponse.bodyToMono(String.class),
                                duration);
                    })
                    .doOnError(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.error("Error making request: {} ({} ms)", throwable.getMessage(), duration);
                    });
        })
        .build();

// 
// 
WebClient webClient = WebClient.builder()
        .baseUrl("http://example.com")
        .filter(ExchangeFilterFunctions.ofRequestProcessor(request -> {
            if (log.isTraceEnabled()) {
                log.trace("Request: {} {} {} {}",
                        request.method(),
                        request.url(),
                        request.headers(),
                        request.bodyToMono(String.class));
            } else {
                log.debug("Request: {} {}",
                        request.method(),
                        request.url());
            }

            return request;
        }))
        .filter((clientRequest, next) -> {
            long startTime = System.currentTimeMillis();

            return next.exchange(clientRequest)
                    .doOnSuccess(clientResponse -> {
                        long duration = System.currentTimeMillis() - startTime;

                        if (log.isTraceEnabled()) {
                            log.trace("Response: {} {} {} {} ({} ms)",
                                    clientResponse.statusCode(),
                                    clientResponse.statusCode().getReasonPhrase(),
                                    clientResponse.headers().asHttpHeaders(),
                                    clientResponse.bodyToMono(String.class),
                                    duration);
                        } else {
                            log.debug("Response: {} {} ({} ms)",
                                    clientResponse.statusCode(),
                                    clientResponse.statusCode().getReasonPhrase(),
                                    duration);
                        }
                    })
                    .doOnError(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.error("Error making request: {} ({} ms)", throwable.getMessage(), duration);
                    });
        })
        .build();


// No check for Trace or Debug level

WebClient webClient = WebClient.builder()
        .baseUrl("http://example.com")
        .filter(ExchangeFilterFunctions.ofRequestProcessor(request -> {
            log.trace("Request: {} {} {} {}",
                    request.method(),
                    request.url(),
                    request.headers(),
                    request.bodyToMono(String.class));

            return request;
        }))
        .filter((clientRequest, next) -> {
            long startTime = System.currentTimeMillis();

            return next.exchange(clientRequest)
                    .doOnSuccess(clientResponse -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.trace("Response: {} {} {} {} ({} ms)",
                                clientResponse.statusCode(),
                                clientResponse.statusCode().getReasonPhrase(),
                                clientResponse.headers().asHttpHeaders(),
                                clientResponse.bodyToMono(String.class),
                                duration);
                    })
                    .doOnError(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;

                        log.error("Error making request: {} ({} ms)", throwable.getMessage(), duration);
                    });
        })
        .build();
