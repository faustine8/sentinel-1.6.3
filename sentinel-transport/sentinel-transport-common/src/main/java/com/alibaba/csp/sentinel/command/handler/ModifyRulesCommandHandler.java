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
package com.alibaba.csp.sentinel.command.handler;

import java.net.URLDecoder;
import java.util.List;

import com.alibaba.csp.sentinel.command.CommandHandler;
import com.alibaba.csp.sentinel.command.CommandRequest;
import com.alibaba.csp.sentinel.command.CommandResponse;
import com.alibaba.csp.sentinel.command.annotation.CommandMapping;
import com.alibaba.csp.sentinel.datasource.WritableDataSource;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.fastjson.JSONArray;

import static com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry.*;

/**
 * @author jialiang.linjl
 * @author Eric Zhao
 */
@CommandMapping(name = "setRules", desc = "modify the rules, accept param: type={ruleType}&data={ruleJson}")
public class ModifyRulesCommandHandler implements CommandHandler<String> {

    @Override
    public CommandResponse<String> handle(CommandRequest request) {
        // 获取规则类型 (是流控还是降级)
        String type = request.getParam("type");
        // rule data in get parameter
        // 获取参数数据
        String data = request.getParam("data");
        if (StringUtil.isNotEmpty(data)) {
            try {
                data = URLDecoder.decode(data, "utf-8");
            } catch (Exception e) {
                RecordLog.info("Decode rule data error", e);
                return CommandResponse.ofFailure(e, "decode rule data error");
            }
        }

        RecordLog.info(String.format("Receiving rule change (type: %s): %s", type, data));

        String result = "success";

        // 限流处理
        if (FLOW_RULE_TYPE.equalsIgnoreCase(type)) {
            // 将参数数据恢复成「流控规则」列表
            List<FlowRule> flowRules = JSONArray.parseArray(data, FlowRule.class);
            // 加载限流规则
            FlowRuleManager.loadRules(flowRules);
            if (!writeToDataSource(getFlowDataSource(), flowRules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            return CommandResponse.ofSuccess(result);
        }
        // 授权处理
        else if (AUTHORITY_RULE_TYPE.equalsIgnoreCase(type)) {
            List<AuthorityRule> rules = JSONArray.parseArray(data, AuthorityRule.class);
            AuthorityRuleManager.loadRules(rules);
            if (!writeToDataSource(getAuthorityDataSource(), rules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            return CommandResponse.ofSuccess(result);
        }
        // 降级(熔断)处理
        else if (DEGRADE_RULE_TYPE.equalsIgnoreCase(type)) {
            List<DegradeRule> rules = JSONArray.parseArray(data, DegradeRule.class);
            DegradeRuleManager.loadRules(rules);
            if (!writeToDataSource(getDegradeDataSource(), rules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            return CommandResponse.ofSuccess(result);
        }
        // 系统规则处理
        else if (SYSTEM_RULE_TYPE.equalsIgnoreCase(type)) {
            List<SystemRule> rules = JSONArray.parseArray(data, SystemRule.class);
            SystemRuleManager.loadRules(rules);
            if (!writeToDataSource(getSystemSource(), rules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            return CommandResponse.ofSuccess(result);
        }
        return CommandResponse.ofFailure(new IllegalArgumentException("invalid type"));
    }

    /**
     * Write target value to given data source.
     *
     * @param dataSource writable data source
     * @param value target value to save
     * @param <T> value type
     * @return true if write successful or data source is empty; false if error occurs
     */
    private <T> boolean writeToDataSource(WritableDataSource<T> dataSource, T value) {
        if (dataSource != null) {
            try {
                dataSource.write(value);
            } catch (Exception e) {
                RecordLog.warn("Write data source failed", e);
                return false;
            }
        }
        return true;
    }

    private static final String WRITE_DS_FAILURE_MSG = "partial success (write data source failed)";
    private static final String FLOW_RULE_TYPE = "flow";
    private static final String DEGRADE_RULE_TYPE = "degrade";
    private static final String SYSTEM_RULE_TYPE = "system";
    private static final String AUTHORITY_RULE_TYPE = "authority";
}
