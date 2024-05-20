package com.zm.ojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.Arratil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.zm.ojcodesandbox.model.ExecuteCodeRequest;
import com.zm.ojcodesandbox.model.ExecuteCodeResponse;
import com.zm.ojcodesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 5000L;

    private static Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 3、创建容器，把文件复制到容器内
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 获取默认的 Docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        //拉取镜像
        //alpine轻量版
        String image = "openjdk:8-alpine";
        final int[] maxRetryAttempts = {3};
        final int[] retryCount = {0};

        if (FIRST_INIT) {
            while (retryCount[0] < maxRetryAttempts[0]) {
                try {
                    PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
                    PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                        @Override
                        public void onNext(PullResponseItem item) {
                            System.out.println("下载镜像：" + item.getStatus());
                            super.onNext(item);
                        }

                        @Override
                        public void onComplete() {
                            System.out.println("镜像拉取完成");
                            FIRST_INIT = false; // 成功拉取后，设置为 false
                            super.onComplete();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            System.out.println("拉取镜像时发生错误");
                            throwable.printStackTrace();
                            retryCount[0]++;
                            if (retryCount[0] >= maxRetryAttempts[0]) {
                                System.out.println("达到最大重试次数，不再尝试");
                                // 可以选择在此处记录日志，或者通知用户
                                // 并决定是否需要恢复 FIRST_INIT 为 true
                            } else {
                                System.out.println("重试拉取镜像，已尝试 " + retryCount[0] + " 次");
                            }
                            super.onError(throwable);
                        }
                    };

                    pullImageCmd
                            .exec(pullImageResultCallback)
                            .awaitCompletion();
                    break; // 拉取成功，跳出循环
                } catch (InterruptedException e) {
                    System.out.println("拉取镜像异常");
                    Thread.currentThread().interrupt(); // 保持中断状态
                    throw new RuntimeException(e);
                }
            }
        }

        System.out.println("下载完成");

        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //初始化宿主机配置
        HostConfig hostConfig = new HostConfig();
        //最大内存限制
        hostConfig.withMemory(100 * 1000 * 1000L);
        //内存交换，即写入磁盘，定义为0可以增强稳定性，设为0不一定最好
        hostConfig.withMemorySwap(0L);
        //限制使用cpu的核数  Percent:争夺cpu的权重  Period和Quota是分配时间片，难以控制不建议用  Shares:使用cpu的占比（相比其他容器）
        hostConfig.withCpuCount(1L);
        //配置安全策略
        String seccompConfig="{\n" +
                "  \"defaultAction\": \"SCMP_ACT_ERRNO\",\n" +
                "  \"syscalls\": [\n" +
                "    {\n" +
                "      \"name\": \"execve\",\n" +
                "      \"action\": \"SCMP_ACT_ALLOW\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"exit_group\",\n" +
                "      \"action\": \"SCMP_ACT_ALLOW\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"write\",\n" +
                "      \"action\": \"SCMP_ACT_ALLOW\",\n" +
                "      \"args\": [\n" +
                "        {\n" +
                "          \"index\": 0,\n" +
                "          \"value\": 1,\n" +
                "          \"valueTwo\": 0,\n" +
                "          \"op\": \"SCMP_CMP_EQ\"  // 只允许写入到标准输出（文件描述符1）\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"read\",\n" +
                "      \"action\": \"SCMP_ACT_ALLOW\",\n" +
                "      \"args\": [\n" +
                "        {\n" +
                "          \"index\": 0,\n" +
                "          \"value\": 0,\n" +
                "          \"valueTwo\": 0,\n" +
                "          \"op\": \"SCMP_CMP_EQ\"  // 只允许从标准输入（文件描述符0）读取\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"*\",\n" +
                "      \"action\": \"SCMP_ACT_KILL\"  // 默认情况下阻止所有其他系统调用\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
        //挂载数据卷
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        //执行命令创建容器
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                //禁止网络连接
                .withNetworkDisabled(true)
                //禁止修改文件
                .withReadonlyRootfs(true)
                //允许连接到容器的标准输入、标准错误和标准输出。
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                //启用tty分配，使得容器支持终端交互
                .withTty(true)
                .exec();

        //打印结果，获得容器id
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //docker exec upbeat_ramanujan java -cp /app Main 1 3
        //要把参数拆开传递，如果放一起“1 3”会传入字符串而不是两个数字
        //执行命令并获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        for (String inputArgs : inputList) {

            //生成计时器
            StopWatch stopWatch = new StopWatch();

            //把输入按空格分成数组拆开传递
            String[] inputArgsArray = inputArgs.split(" ");

            //构造执行命令
            String[] cmdArray = Arratil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);

            //执行命令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();

            System.out.println("创建执行命令：" + execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            //内部类需要用一个相对来说不会改变的变量防止变量错乱，alt+enter自动转换为如下引用变量
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            StringBuilder stringBuilder = new StringBuilder();

            // 判断是否超时
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId();

            StringBuilder stdoutBuilder = new StringBuilder();
            StringBuilder stderrBuilder = new StringBuilder();

            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    // 如果执行完成，则表示没超时
                    timeout[0] = false;

                    // 在这里将StringBuilder转换为String
                    message[0] = stdoutBuilder.toString();
                    errorMessage[0] = stderrBuilder.toString();

                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();

                    if (StreamType.STDERR.equals(streamType)) {
                        stderrBuilder.append(new String(frame.getPayload()));
                        // 仅用于即时打印错误信息，可选
                        System.out.println("输出错误结果：" + stderrBuilder.toString());
                    } else {
                        stdoutBuilder.append(new String(frame.getPayload()));
                        // 仅用于即时打印正常输出，可选
                        System.out.println("输出结果：" + stdoutBuilder.toString());
                    }

                    super.onNext(frame);
                }
            };


            final long[] maxMemory = {0L};

            // 得到获取容器状态的命令
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);

            //定义获取内存的回调函数
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {
                }

                @Override
                public void onStart(Closeable closeable) {
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });

            //执行获取内存的命令
            statsCmd.exec(statisticsResultCallback);

            try {
                //开始计时
                stopWatch.start();

                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);

                //获得执行时间
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();

                //关闭内存监控
                statsCmd.close();

            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }

            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }
}



