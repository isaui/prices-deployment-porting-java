package com.prices.cli.api;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;

public class ProgressBodyPublisher implements HttpRequest.BodyPublisher {

    private final HttpRequest.BodyPublisher delegate;
    private final long contentLength;
    private final BiConsumer<Long, Long> progressCallback;

    public ProgressBodyPublisher(HttpRequest.BodyPublisher delegate, BiConsumer<Long, Long> progressCallback) {
        this.delegate = delegate;
        this.contentLength = delegate.contentLength();
        this.progressCallback = progressCallback;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(new ProgressSubscriber(subscriber, contentLength, progressCallback));
    }

    private static class ProgressSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final Flow.Subscriber<? super ByteBuffer> delegate;
        private final long totalBytes;
        private final BiConsumer<Long, Long> progressCallback;
        private long bytesWritten = 0;

        ProgressSubscriber(Flow.Subscriber<? super ByteBuffer> delegate, long totalBytes, 
                          BiConsumer<Long, Long> progressCallback) {
            this.delegate = delegate;
            this.totalBytes = totalBytes;
            this.progressCallback = progressCallback;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(ByteBuffer item) {
            bytesWritten += item.remaining();
            progressCallback.accept(bytesWritten, totalBytes);
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }
}
