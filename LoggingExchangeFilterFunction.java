import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public class CustomLoggingFilter implements ExchangeFunction {

    private static final Logger logger = LoggerFactory.getLogger(CustomLoggingFilter.class);

    private final ExchangeFunction delegate;

    public CustomLoggingFilter(ExchangeFunction delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<ClientHttpResponse> exchange(ClientHttpRequest request) {
        logRequest(request);
        Instant requestTime = Instant.now();
        return delegate.exchange(request)
                       .doOnNext(response -> logResponse(request.getMethod(), request.getURI()
                                                                                     .toString(), request.getHeaders(), response,
                               requestTime));
    }

    private void logRequest(ClientHttpRequest request) {
        HttpMethod method = request.getMethod();
        String url = request.getURI()
                            .toString();
        HttpHeaders headers = request.getHeaders();
        Object requestBody = null;
        if (logger.isDebugEnabled()) {
            requestBody = request.getBody();
        }
        logger.debug("[Request] Method: {}, URL: {}, Headers: {}, Body: {}", method, url, headers, requestBody);
    }

    private void logResponse(HttpMethod method, String url, HttpHeaders requestHeaders, @Nullable ClientHttpResponse response, Instant requestTime) {
        Instant responseTime = Instant.now();
        long durationMs = Duration.between(requestTime, responseTime)
                                  .toMillis();

        if (response != null) {
            HttpStatus status = response.getStatusCode();
            HttpHeaders headers = response.getHeaders();
            Object responseBody = null;
            if (logger.isDebugEnabled()) {
                responseBody = readResponseBody(response);
            }
            logger.debug("[Response] Method: {}, URL: {}, Status: {}, Headers: {}, Duration: {} ms, ResponseBody: {}", method,
                    url, status, headers, durationMs, responseBody);
        } else {
            logger.debug("[Response] Method: {}, URL: {}, Status: [NO RESPONSE], Headers: {}, Duration: {} ms", method, url,
                    requestHeaders, durationMs);
        }
    }

    private Object readResponseBody(ClientHttpResponse response) {
        Flux<DataBuffer> flux = response.getBody();
        StringBuilder builder = new StringBuilder();
        flux.subscribe(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            builder.append(new String(bytes, StandardCharsets.UTF_8));
        });
        return builder.toString();
    }

    @Override
    public Mono<ClientHttpResponse> exchange(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        logRequest(request);
        Instant requestTime = Instant.now();
        return delegate.exchange(exchange)
                       .doOnNext(response -> logResponse(request.getMethod(), request.getURI()
                                                                                     .toString(), request.getHeaders(), response,
                               requestTime));
    }

    private void logRequest(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        String url = request.getURI()
                            .toString();
        HttpHeaders headers = request.getHeaders();
        Object requestBody = null;
        if (logger.isDebugEnabled()) {
            requestBody = request.getBody();
        }
        logger.debug("[Request] Method: {}, URL: {}, Headers: {}, Body: {}", method, url, headers, requestBody);
    }

}
