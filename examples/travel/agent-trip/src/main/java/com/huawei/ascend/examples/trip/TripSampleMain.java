/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip;

import com.huawei.ascend.examples.trip.hotel.HotelPlannerClient;
import com.huawei.ascend.examples.trip.hotel.ReflectiveHotelPlannerClient;
import com.openjiuwen.core.runner.Runner;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * 演示「主规划(入参) → 行程规划 ReAct → 酒店子智能体」串调（端到端真链路）。
 * <p>酒店子智能体经 {@link ReflectiveHotelPlannerClient} <b>反射装配</b>：agent-trip 对 agent-hotel
 * 无编译期依赖，agent-hotel 仅需在运行时 classpath（pom 中为 runtime scope）。
 * <p>行程规划的 plan_hotel 工具会触发酒店子智能体自己的 ReAct（ReAct 套 ReAct）。
 */
public final class TripSampleMain {

    private TripSampleMain() {
    }

    public static void main(String[] args) throws Exception {
        // 强制控制台输出用 UTF-8，避免 Windows 默认 GBK 导致中文乱码
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        LlmConfig llm = LlmConfig.load();
        // 1) 反射装配酒店子智能体（不依赖 agent-hotel 编译期类型）+ 行程规划智能体
        HotelPlannerClient hotelClient = new ReflectiveHotelPlannerClient(
                llm.provider(), llm.apiKey(), llm.apiBase(), llm.modelName(), llm.sslVerify());
        TripPlanningAgent tripAgent = new TripPlanningAgent(llm, hotelClient);

        try {
            // 2) 输入来源优先级：命令行参数 > query.txt（UTF-8，推荐，避免控制台编码问题）> 交互输入
            if (args.length > 0) {
                System.out.println(tripAgent.chat(String.join(" ", args)));
                return;
            }

            Path queryFile = Path.of("query.txt");
            if (Files.exists(queryFile)) {
                String query = Files.readString(queryFile, StandardCharsets.UTF_8).trim();
                System.out.println("【query.txt】" + query);
                System.out.println(tripAgent.chat(query));
                return;
            }

            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            System.out.println("请输入差旅诉求（输入 exit 退出）：");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(line)) {
                    break;
                }
                System.out.println(tripAgent.chat(line));
                System.out.println("\n请输入差旅诉求（输入 exit 退出）：");
            }
        } finally {
            Runner.stop();
        }
    }
}
