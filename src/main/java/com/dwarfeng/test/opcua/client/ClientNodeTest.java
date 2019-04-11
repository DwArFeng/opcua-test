package com.dwarfeng.test.opcua.client;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.nodes.Node;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import com.dwarfeng.dutil.basic.io.CT;

public class ClientNodeTest {

	public static void main(String[] args) throws Exception {

		// 定义 endpoint 的 URL。
		String endPointUrl = "opc.tcp://192.9.100.161:49320";

		// 通过 endpoint 的 URL 搜索出所有的 endpoint 描述。
		EndpointDescription[] endpointDescriptions = UaTcpStackClient.getEndpoints(endPointUrl).get();

		// 循环打印 endpoint 描述
		Arrays.stream(endpointDescriptions).forEach(e -> CT.trace(e));

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

		// 通过指定的配置生成 opcua 客户端。
		OpcUaClient opcUaClient = new OpcUaClient(config);
		try {
			// 客户端连接服务端。
			opcUaClient.connect().get();

			// ------------------------------------------------------------------//
			CT.trace("打印根目录下的所有子节点");
			// 获取根节点的子节点。
			List<Node> nodes = opcUaClient.getAddressSpace().browse(Identifiers.RootFolder).get();
			nodes.stream().forEach(node -> {
				try {
					CT.trace(String.format("NodeID: %s, DisplayName: %s", node.getNodeId().get(),
							node.getDisplayName().get()));
				} catch (InterruptedException | ExecutionException e1) {
					e1.printStackTrace();
				}
			});

			// ------------------------------------------------------------------//
			CT.trace("手动指定特定的节点");
			// 手动指定一个特定的节点ID。
			NodeId specifiedNodeId = new NodeId(UShort.valueOf(2), "d6.090-046.AATORQUE");
			// 读取特定节点的数据值。
			DataValue dataValue = opcUaClient.readValue(0.0, TimestampsToReturn.Both, specifiedNodeId).get();

			// 获取指定的节点ID对应的节点。
			Node specifiedNode = opcUaClient.getAddressSpace().getNodeInstance(specifiedNodeId).get();
			// 显示指定的节点的详细信息。
			CT.trace(String.format("NodeID: %s, DisplayName: %s", specifiedNode.getNodeId().get(),
					specifiedNode.getDisplayName().get()));
			// 输出当前值。
			CT.trace(String.format("节点的当前值为: %s", dataValue.getValue().getValue()));
		} finally {
			// 在 finally 代码块中释放连接
			Optional.ofNullable(opcUaClient).ifPresent(client -> {
				try {
					client.disconnect().get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			});
		}

	}

}
