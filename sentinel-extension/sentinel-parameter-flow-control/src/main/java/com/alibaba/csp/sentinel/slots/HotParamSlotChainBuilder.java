/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots;

import com.alibaba.csp.sentinel.slotchain.DefaultProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.SlotChainBuilder;
import com.alibaba.csp.sentinel.slots.block.authority.AuthoritySlot;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeSlot;
import com.alibaba.csp.sentinel.slots.block.flow.FlowSlot;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowSlot;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.slots.logger.LogSlot;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;
import com.alibaba.csp.sentinel.slots.statistic.StatisticSlot;
import com.alibaba.csp.sentinel.slots.system.SystemSlot;

/**
 * @author Eric Zhao
 * @since 0.2.0
 */
public class HotParamSlotChainBuilder implements SlotChainBuilder {

    @Override
    public ProcessorSlotChain build() {
        ProcessorSlotChain chain = new DefaultProcessorSlotChain();
        // 负责收集资源的路径，并将这些资源的调用路径，以树状结构存储起来，用于根据调用路径来限流降级
        chain.addLast(new NodeSelectorSlot());
        // 用于构建资源的 ClusterNode 以及调用来源节点。ClusterNode 保持某个资源运行统计信息(响应时间、QPS、block 数目、线程数、异常数等)以及调用来源统计信息列表
        chain.addLast(new ClusterBuilderSlot());
        // 该类对链路的传递不做处理，只有在抛出 BlockException 的时候，向上层层传递的过程中，会通过该类来输入一些日志信息
        chain.addLast(new LogSlot());
        // 用于记录、统计不同维度的运行指标监控信息
        chain.addLast(new StatisticSlot());
        // 用于频繁(“热点”)参数进行流量控制。
        chain.addLast(new ParamFlowSlot());
        // 根据配置的黑白名单和调用来源信息，来做黑白名单控制。
        chain.addLast(new SystemSlot());
        // 会根据对于当前系统的整体情况，对入口资源的调用进行动态调配。其原理是让入口的流量和当前系统的预计容量达到一个动态平衡。
        chain.addLast(new AuthoritySlot());
        // 主要完成限流
        chain.addLast(new FlowSlot());
        // 熔断。主要针对资源的平均响应时间(RT)以及异常比率，来决定资源是否在接下来的时间被自 动熔断掉。
        chain.addLast(new DegradeSlot());

        return chain;
    }
}
