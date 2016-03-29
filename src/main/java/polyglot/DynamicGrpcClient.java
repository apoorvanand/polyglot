package polyglot;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auth.Credentials;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;

/** A grpc client which operates on dynamic messages. */
public class DynamicGrpcClient {
  private static final Logger logger = LoggerFactory.getLogger(DynamicGrpcClient.class);
  private final MethodDescriptor protoMethodDescriptor;
  private final Channel channel;

  /** Creates a client for the supplied method, talking to the supplied endpoint. */
  public static DynamicGrpcClient create(MethodDescriptor protoMethod, String host, int port) {
    return new DynamicGrpcClient(protoMethod, newChannel(host, port));
  }

  /**
   * Creates a client for the supplied method, talking to the supplied endpoint. Passes the supplied
   * credentials on every rpc call.
   */
  public static DynamicGrpcClient createWithCredentials(
      MethodDescriptor protoMethod, String host, int port, Credentials credentials) {
    ExecutorService credentialsExecutor = Executors.newCachedThreadPool();
    Channel channel = ClientInterceptors.intercept(
        newChannel(host, port),
        new ClientAuthInterceptor(credentials, credentialsExecutor));
    return new DynamicGrpcClient(protoMethod, channel);
  }

  private DynamicGrpcClient(MethodDescriptor protoMethodDescriptor, Channel channel) {
    this.protoMethodDescriptor = protoMethodDescriptor;
    this.channel = channel;
    logger.info("Created client for method: " + getFullMethodName());
  }

  /** Makes an rpc to the remote endpoint and returns the remote response. */
  public ListenableFuture<DynamicMessage> call(DynamicMessage request) {
    return ClientCalls.futureUnaryCall(
        channel.newCall(createGrpcMethodDescriptor(), CallOptions.DEFAULT),
        request);
  }

  private String getFullMethodName() {
    String serviceName = protoMethodDescriptor.getService().getFullName();
    String methodName = protoMethodDescriptor.getName();
    return io.grpc.MethodDescriptor.generateFullMethodName(serviceName, methodName);
  }

  private io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> createGrpcMethodDescriptor() {
    return io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>create(
        MethodType.UNARY,
        getFullMethodName(),
        new DynamicMessageMarshaller(protoMethodDescriptor.getInputType()),
        new DynamicMessageMarshaller(protoMethodDescriptor.getOutputType()));
  }

  private static Channel newChannel(String host, int port) {
    return NettyChannelBuilder.forAddress(host, port)
        .negotiationType(NegotiationType.PLAINTEXT)
        .build();
  }
}
