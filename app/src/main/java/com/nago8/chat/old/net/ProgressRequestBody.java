package com.nago8.chat.old.net;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

/**
 * 包装 RequestBody 以监听网络字节上传进度
 */
public class ProgressRequestBody extends RequestBody {

    public interface ProgressListener {
        void onProgress(long bytesWritten, long contentLength, int percent);
    }

    private final RequestBody delegate;
    private final ProgressListener listener;
    private CountingSink countingSink;

    public ProgressRequestBody(RequestBody delegate, ProgressListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public MediaType contentType() {
        return delegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return delegate.contentLength();
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        countingSink = new CountingSink(sink);
        BufferedSink bufferedSink = Okio.buffer(countingSink);
        delegate.writeTo(bufferedSink);
        bufferedSink.flush();
    }

    private final class CountingSink extends ForwardingSink {
        private long bytesWritten = 0;
        private long contentLength = 0;

        CountingSink(Sink delegate) {
            super(delegate);
        }

        @Override
        public void write(@NonNull Buffer source, long byteCount) throws IOException {
            super.write(source, byteCount);
            bytesWritten += byteCount;
            if (contentLength <= 0) {
                try {
                    contentLength = contentLength();
                } catch (Exception ignored) {}
            }
            if (listener != null) {
                int percent = contentLength > 0 ? (int) ((bytesWritten * 100) / contentLength) : 0;
                if (percent > 100) percent = 100;
                listener.onProgress(bytesWritten, contentLength, percent);
            }
        }
    }
}
