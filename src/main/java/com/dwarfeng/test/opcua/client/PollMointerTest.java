package com.dwarfeng.test.opcua.client;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.nodes.Node;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import com.dwarfeng.dutil.basic.io.CT;
import com.dwarfeng.dutil.develop.timer.ListTimer;
import com.dwarfeng.dutil.develop.timer.Timer;
import com.dwarfeng.dutil.develop.timer.plain.FixedRatePlain;

public class PollMointerTest {

	public static void main(String[] args) throws Exception {

		// 定义轮询间隔（毫秒）。
		final long pollPeriod = 1000;

		// 定义 client 的 endpoint 的 URL。
		final String endPointUrl = "opc.tcp://192.9.100.161:49320";
		// 定义 nodeId 的 namespaceIndex。
		final UShort namespaceIndex = UShort.valueOf(2);
		// 定义 nodeId 的 identifier。
		final String identifier = "d6.090-046.AATORQUE";

		Timer timer = null;
		Scanner scanner = null;

		OpcUaClient client = null;
		NodeId nodeId = null;
		try {
			timer = new ListTimer();
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

			CT.trace("正在将指定的节点加入轮询查询，输入任意内容解除轮询...");

			NodeMonitorPlain nodeMonitorPlain = new NodeMonitorPlain(pollPeriod, 0, client, nodeId);
			timer.schedule(nodeMonitorPlain);

			scanner.nextLine();

			// 解除轮询。
			timer.remove(nodeMonitorPlain);
			CT.trace("轮询已经解除，输入任何内容退出程序...");

			scanner.nextLine();

			System.exit(0);
		} finally {
			Optional.ofNullable(timer).ifPresent(t -> t.shutdown());
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

class NodeMonitorPlain extends FixedRatePlain {

	/** 指定的 OPC UA 客户端。 */
	private final OpcUaClient client;

	/** 需要被监视的 nodeId */
	private final NodeId nodeId;

	public NodeMonitorPlain(long period, long nextRunOffset, OpcUaClient client, NodeId nodeId) {
		super(period, nextRunOffset);

		Objects.requireNonNull(client, "入口参数 client 不能为 null。");
		Objects.requireNonNull(nodeId, "入口参数 nodeId 不能为 null。");

		this.client = client;
		this.nodeId = nodeId;
	}

	@Override
	protected void todo() throws Exception {
		// 打印滴答声。
		CT.trace("tick..");

		// 获取指定ID对应的可变节点。
		VariableNode variableNode = client.getAddressSpace().getVariableNode(nodeId).get();
		// 在可变节点中读取值。
		DataValue dataValue = variableNode.readValue().get();
		// 获取指定的节点ID对应的节点。
		Node node = client.getAddressSpace().getNodeInstance(nodeId).get();
		// 显示指定的节点的详细信息。
		CT.trace(String.format("NodeID: %s, DisplayName: %s, CurrentValue: %s", node.getNodeId().get(),
				node.getDisplayName().get(), dataValue.getValue().getValue()));
	}

// 另一种实现方法。
//	@Override
//	protected void todo() throws Exception {
//		// 打印滴答声。
//		CT.trace("tick..");
//
//		// 读取特定节点的数据值。
//		DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get();
//		// 获取指定的节点ID对应的节点。
//		Node node = client.getAddressSpace().getNodeInstance(nodeId).get();
//		// 显示指定的节点的详细信息。
//		CT.trace(String.format("NodeID: %s, DisplayName: %s, CurrentValue: %s", node.getNodeId().get(),
//				node.getDisplayName().get(), dataValue.getValue().getValue()));
//	}

}
