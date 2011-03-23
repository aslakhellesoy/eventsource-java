package com.biasedbit.nettytutorials.handshake.client;

import com.biasedbit.nettytutorials.handshake.common.HandshakeEvent;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:bruno@biasedbit.com">Bruno de Carvalho</a>
 */
public class ClientHandshakeHandler extends SimpleChannelHandler {

    // internal vars ----------------------------------------------------------

    private final long timeoutInMillis;
    private final AtomicBoolean handshakeComplete;
    private final AtomicBoolean handshakeFailed;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Object handshakeMutex = new Object();
    private final URI uri;

    // constructors -----------------------------------------------------------

    public ClientHandshakeHandler(long timeoutInMillis, URI uri) {
        this.timeoutInMillis = timeoutInMillis;
        this.uri = uri;
        this.handshakeComplete = new AtomicBoolean(false);
        this.handshakeFailed = new AtomicBoolean(false);
    }

    // SimpleChannelHandler ---------------------------------------------------

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        System.out.println("MESSAGE");
        if (this.handshakeFailed.get()) {
            // Bail out fast if handshake already failed
            return;
        }

        if (this.handshakeComplete.get()) {
            // If handshake succeeded but message still came through this
            // handler, then immediately send it upwards.
            // Chances are it's the last time a message passes through
            // this handler...
            super.messageReceived(ctx, e);
            return;
        }

        synchronized (this.handshakeMutex) {
            // Recheck conditions after locking the mutex.
            // Things might have changed while waiting for the lock.
            if (this.handshakeFailed.get()) {
                return;
            }

            if (this.handshakeComplete.get()) {
                super.messageReceived(ctx, e);
                return;
            }

            // Parse the challenge.
            // Expected format is "clientId:serverId:challenge"

            HttpResponse response = (HttpResponse) e.getMessage();

            if (!HttpResponseStatus.OK.equals(response.getStatus())) {
                out("BAD STATUS. EXPECTED 200, GOT " + response.getStatus());
                this.fireHandshakeFailed(ctx);
                return;
            }

            if (!"text/event-stream".equals(response.getHeader("Content-Type"))) {
                out("BAD CONTENT TYPE. EXPECTED text/event-stream, GOT " + response.getHeader("Content-Type"));
                this.fireHandshakeFailed(ctx);
                return;
            }

            // TODO: check other things like absence of content-length, 

            // Everything went okay!
            out("--- CLIENT-HS :: Challenge validated, flushing messages & " +
                    "removing handshake handler from pipeline.");

            // Remove this handler from the pipeline; its job is finished.
//            ctx.getPipeline().remove(this);

            ctx.getPipeline().remove(HttpRequestEncoder.class);
            ctx.getPipeline().remove(HttpResponseDecoder.class);
            ctx.getPipeline().remove(this);

            ctx.getPipeline().addLast("lines", new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()));
            ctx.getPipeline().addLast("strings", new StringDecoder());
            ctx.getPipeline().addLast("events", new SimpleChannelUpstreamHandler() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                    System.out.println("LINE:" + e.getMessage());
                    super.messageReceived(ctx, e);
                }
            });

            // Finally fire success message upwards.
            this.fireHandshakeSucceeded(ctx);
        }
    }

    @Override
    public void channelConnected(final ChannelHandlerContext ctx,
                                 ChannelStateEvent e) throws Exception {
        out("--- CLIENT-HS :: Outgoing connection established to: " +
                e.getChannel().getRemoteAddress());

        // Write the handshake & add a timeout listener.
        ChannelFuture f = Channels.future(ctx.getChannel());
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                // Once this message is sent, start the timeout checker.
                new Thread() {
                    @Override
                    public void run() {
                        // Wait until either handshake completes (releases the
                        // latch) or this latch times out.
                        try {
                            latch.await(timeoutInMillis, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e1) {
                            out("--- CLIENT-HS :: Handshake timeout checker: " +
                                    "interrupted!");
                            e1.printStackTrace();
                        }

                        // Informative output, do nothing...
                        if (handshakeFailed.get()) {
                            out("--- CLIENT-HS :: (pre-synchro) Handshake " +
                                    "timeout checker: discarded " +
                                    "(handshake failed)");
                            return;
                        }

                        // More informative output, do nothing...
                        if (handshakeComplete.get()) {
                            out("--- CLIENT-HS :: (pre-synchro) Handshake " +
                                    "timeout checker: discarded" +
                                    "(handshake completed)");
                            return;
                        }

                        // Handshake has neither failed nor completed, time
                        // to do something! (trigger failure).
                        // Lock on the mutex first...
                        synchronized (handshakeMutex) {
                            // Same checks as before, conditions might have
                            // changed while waiting to get a lock on the
                            // mutex.
                            if (handshakeFailed.get()) {
                                out("--- CLIENT-HS :: (synchro) Handshake " +
                                        "timeout checker: already failed.");
                                return;
                            }

                            if (!handshakeComplete.get()) {
                                // If handshake wasn't completed meanwhile,
                                // time to mark the handshake as having failed.
                                out("--- CLIENT-HS :: (synchro) Handshake " +
                                        "timeout checker: timed out, " +
                                        "killing connection.");
                                fireHandshakeFailed(ctx);
                            } else {
                                // Informative output; the handshake was
                                // completed while this thread was waiting
                                // for a lock on the handshakeMutex.
                                // Do nothing...
                                out("--- CLIENT-HS :: (synchro) Handshake " +
                                        "timeout checker: discarded " +
                                        "(handshake OK)");
                            }
                        }
                    }
                }.start();
            }
        });

        Channel c = ctx.getChannel();
        // Passing null as remoteAddress, since constructor in
        // DownstreamMessageEvent will use remote address from the channel if
        // remoteAddress is null.
        // Also, we need to send the data directly downstream rather than
        // call c.write() otherwise the message would pass through this
        // class's writeRequested() method defined below.

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toString());
        request.addHeader(Names.ACCEPT, "text/event-stream");
        request.addHeader(Names.HOST, uri.getHost());
        request.addHeader(Names.ORIGIN, "http://" + uri.getHost());
        request.addHeader(Names.CACHE_CONTROL, "no-cache");
        ctx.sendDownstream(new DownstreamMessageEvent(c, f, request, null));
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        out("--- CLIENT-HS :: Channel closed.");
        if (!this.handshakeComplete.get()) {
            this.fireHandshakeFailed(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        out("--- CLIENT-HS :: Exception caught.");
        e.getCause().printStackTrace();
        if (e.getChannel().isConnected()) {
            // Closing the channel will trigger handshake failure.
            e.getChannel().close();
        } else {
            // Channel didn't open, so we must fire handshake failure directly.
            this.fireHandshakeFailed(ctx);
        }
    }

    // private static helpers -------------------------------------------------

    private static void out(String s) {
        System.out.println(s);
    }

    // private helpers --------------------------------------------------------

    private void fireHandshakeFailed(ChannelHandlerContext ctx) {
        this.handshakeComplete.set(true);
        this.handshakeFailed.set(true);
        this.latch.countDown();
        ctx.getChannel().close();
        ctx.sendUpstream(HandshakeEvent.handshakeFailed(ctx.getChannel()));
    }

    private void fireHandshakeSucceeded(ChannelHandlerContext ctx) {
        this.handshakeComplete.set(true);
        this.handshakeFailed.set(false);
        this.latch.countDown();
        ctx.sendUpstream(HandshakeEvent.handshakeSucceeded(ctx.getChannel()));
    }
}
