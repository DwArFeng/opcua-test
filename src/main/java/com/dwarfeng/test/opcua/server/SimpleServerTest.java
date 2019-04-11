package com.dwarfeng.test.opcua.server;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_X509;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.eclipse.milo.examples.server.ExampleNamespace;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.application.InsecureCertificateValidator;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;

import com.dwarfeng.dutil.basic.io.CT;
import com.google.common.collect.ImmutableList;

public class SimpleServerTest {

	public static void main(String[] args) throws Exception {
		Scanner scanner = null;

		try {
			scanner = new Scanner(System.in);

			List<String> endpointAddresses = newArrayList();
			endpointAddresses.add("localhost");

			OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()//
					.setApplicationUri("urn:com.dwarfeng.test.opcua.server.application")//
					.setApplicationName(LocalizedText.english("Simple OPC UA Test Server"))//
					.setBindPort(12450)//
					.setBuildInfo(new BuildInfo("", "dwarfeng", "simple opc ua test server", OpcUaServer.SDK_VERSION,
							"", DateTime.now()))//
					.setCertificateManager(new DefaultCertificateManager())//
					.setCertificateValidator(new InsecureCertificateValidator())//
					.setEndpointAddresses(endpointAddresses)//
					.setProductUri("urn:com.dwarfeng.test.opcua.server.product")//
					.setServerName("test-server")//
					.setUserTokenPolicies(ImmutableList.of(USER_TOKEN_POLICY_ANONYMOUS, USER_TOKEN_POLICY_USERNAME,
							USER_TOKEN_POLICY_X509))
					.build();

			// 构造服务器。
			OpcUaServer server = new OpcUaServer(serverConfig);

			// 添加 milo 的示例 namespace，其中包含丰富的变量。
			server.getNamespaceManager().registerAndAdd(ExampleNamespace.NAMESPACE_URI,
					idx -> new ExampleNamespace(server, idx));

			// 启动服务器。
			server.startup().get();
			CT.trace("服务端开启完毕，输入任意内容关闭...");

			// 使用 scanner 阻塞程序，只有当用户在控制台输入任意内容后，代码才继续执行。
			scanner.nextLine();

			// 关闭服务器并退出程序。
			server.shutdown().get();
			CT.trace("服务端关闭，程序退出...");
			System.exit(0);

		} finally {
			Optional.ofNullable(scanner).ifPresent(s -> s.close());
		}

	}

}
