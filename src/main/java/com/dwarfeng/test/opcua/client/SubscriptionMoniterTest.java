package com.dwarfeng.test.opcua.client;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;

import com.dwarfeng.dutil.basic.cna.model.DefaultReferenceModel;
import com.dwarfeng.dutil.basic.cna.model.ReferenceModel;
import com.dwarfeng.dutil.basic.io.CT;

public class SubscriptionMoniterTest {

	public static void main(String[] args) throws Exception {

		// 定义 client 的 endpoint 的 URL。
		final String endPointUrl = "opc.tcp://192.9.100.161:49320";
		// 定义 nodeId 的 namespaceIndex。
		final UShort namespaceIndex = UShort.valueOf(2);
		// 定义 nodeId 的 identifier。
		final String identifier = "d6.090-046.AATORQUE";

		Scanner scanner = null;

		OpcUaClient client = null;
		NodeId nodeId = null;
		try {
			scanner = new Scanner(System.in);

			// 通过 endpoint 的 URL 搜索出所有的 endpoint 描述。
			EndpointDescription[] endpointDescriptions = UaTcpStackClient.getEndpoints(endPointUrl).get();

			// 找到第一个 endpoint 描述，并进行非空判断。
			EndpointDescription endpointDescription = Arrays.stream(endpointDescriptions).findFirst().get();
			if (Objects.isNull(endpointDescription)) {
				CT.trace("没有找到任何 endpoint 描述，程序退出");
				return;
			}

			OpcUaClientConfig config = OpcUaClientConfig.builder().setApplicationName(LocalizedText.english("OPCAPP")) // 设置应用的名称
					.setApplicationUri("urn:LAPTOP-AQ90KJVR:OPCAPP") // 设置应用的 uri
					.setEndpoint(endpointDescription) // 设置服务端的 endpoint 描述。
					.setRequestTimeout(UInteger.valueOf(5000)).build();// 设置超时。

			client = new OpcUaClient(config);
			nodeId = new NodeId(namespaceIndex, identifier);

			// 客户端连接。
			client.connect().get();

			CT.trace("正在为指定的节点注册订阅，输入任意内容解除订阅...");

			// 注册订阅。
			SubcriptionMoniter subcriptionMoniter = new SubcriptionMoniter(client, nodeId,
					SubcriptionMoniter.DEFAULT_CLIENT_HANDLE_GENERATOR);
			subcriptionMoniter.register();

			scanner.nextLine();

			// 解除注册订阅。
			subcriptionMoniter.unregister();
			CT.trace("订阅已经解除，输入任何内容退出程序...");

			scanner.nextLine();

			System.exit(0);
		} finally {
			Optional.ofNullable(scanner).ifPresent(s -> s.close());
			Optional.ofNullable(client).ifPresent(c -> {
				try {
					c.disconnect().get();
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
	}

}

class SubcriptionMoniter {

	/** 默认的客户端句柄生成器。 */
	public static AtomicLong DEFAULT_CLIENT_HANDLE_GENERATOR = new AtomicLong(1L);

	/** 指定的 OPC UA 客户端。 */
	private final OpcUaClient client;

	/** 需要被监视的 nodeId。 */
	private final NodeId nodeId;

	/** 指定的客户端处理器。 */
	private final AtomicLong clientHandleGenerator;

	/** 记录创建的订阅的引用，在解除注册的时候使用。 */
	private final ReferenceModel<UaSubscription> subscriptionRef = new DefaultReferenceModel<>();

	public SubcriptionMoniter(OpcUaClient client, NodeId nodeId, AtomicLong clientHandleGenerator) {
		Objects.requireNonNull(client, "入口参数 client 不能为 null。");
		Objects.requireNonNull(nodeId, "入口参数 nodeId 不能为 null。");
		Objects.requireNonNull(clientHandleGenerator, "入口参数 clientHandleGenerator 不能为 null。");

		this.client = client;
		this.nodeId = nodeId;
		this.clientHandleGenerator = clientHandleGenerator;
	}

	public void register() throws Exception {
		// create a subscription @ 1000ms
		UaSubscription subscription = client.getSubscriptionManager().createSubscription(2000.0).get();

		// subscribe to the Value attribute of the server's CurrentTime node
		ReadValueId readValueId = new ReadValueId(nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

		// important: client handle must be unique per item
		UInteger clientHandle = uint(clientHandleGenerator.getAndIncrement());

		MonitoringParameters parameters = new MonitoringParameters(//
				clientHandle, //
				1000.0, // sampling interval
				null, // filter, null means use default
				uint(10), // queue size
				true // discard oldest
		);

		MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting,
				parameters);

		// when creating items in MonitoringMode.Reporting this callback is where each
		// item needs to have its
		// value/event consumer hooked up. The alternative is to create the item in
		// sampling mode, hook up the
		// consumer after the creation call completes, and then change the mode for all
		// items to reporting.
		BiConsumer<UaMonitoredItem, Integer> onItemCreated = (item, id) -> item
				.setValueConsumer(this::onSubscriptionValue);

		List<UaMonitoredItem> items = subscription
				.createMonitoredItems(TimestampsToReturn.Both, newArrayList(request), onItemCreated).get();

		for (UaMonitoredItem item : items) {
			if (item.getStatusCode().isGood()) {
				CT.trace(String.format("item created for nodeId={%s}", item.getReadValueId().getNodeId()));
			} else {
				CT.trace(String.format("failed to create item for nodeId={%s} (status={%s})",
						item.getReadValueId().getNodeId(), item.getStatusCode()));
			}
		}

		subscriptionRef.set(subscription);
	}

	public void unregister() {
		Optional.ofNullable(subscriptionRef.get())
				.ifPresent(s -> client.getSubscriptionManager().deleteSubscription(s.getSubscriptionId()));
	}

	private void onSubscriptionValue(UaMonitoredItem item, DataValue value) {
		CT.trace(String.format("subscription value received: item={%s}, value={%s}", item.getReadValueId().getNodeId(),
				value.getValue()));
	}

}
