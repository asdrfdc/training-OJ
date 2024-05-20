package com.zm.oj.judge;

import cn.hutool.json.JSONUtil;
import com.zm.oj.common.ErrorCode;
import com.zm.oj.exception.BusinessException;
import com.zm.oj.judge.codesandbox.CodeSandbox;
import com.zm.oj.judge.codesandbox.CodeSandboxFactory;
import com.zm.oj.judge.codesandbox.CodeSandboxProxy;
import com.zm.oj.judge.strategy.JudgeContext;
import com.zm.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.zm.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.zm.oj.model.dto.question.JudgeCase;
import com.zm.oj.judge.codesandbox.model.JudgeInfo;
import com.zm.oj.model.entity.Question;
import com.zm.oj.model.entity.QuestionSubmit;
import com.zm.oj.model.enums.QuestionSubmitStatusEnum;
import com.zm.oj.service.QuestionService;
import com.zm.oj.service.QuestionSubmitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConfigurationProperties
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeManager judgeManager;


    @Value("${codesandbox.type}")
    private String type;


    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {

        // 1）校验，根据传入题目的提交 id，获取到对应的题目、提交信息（包含代码、编程语言等）
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        Long questionId = questionSubmit.getQuestionId();
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }

        // 2）如果题目提交状态不为等待中，就不用重复执行了
        if (!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "拒绝无效提交");
        }

        // 3）更改判题（题目提交）的状态为 “判题中”，防止重复执行
        QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        boolean update = questionSubmitService.updateById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }

        // 4）调用沙箱，获取到执行结果
        // 获取沙箱实现类   工厂模式
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
        codeSandbox = new CodeSandboxProxy(codeSandbox);

        //获取语言和代码
        String language = questionSubmit.getLanguage();
        String code = questionSubmit.getCode();

        // 获取输入用例
        String judgeCaseStr = question.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());

        //构造执行请求
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();

        //调用代码沙箱执行代码
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);

        //得到输出结果
        List<String> outputList = executeCodeResponse.getOutputList();

        // 5）根据沙箱的执行结果，设置题目的判题状态和信息
        JudgeContext judgeContext = new JudgeContext();
        judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
        judgeContext.setInputList(inputList);
        judgeContext.setOutputList(outputList);
        judgeContext.setJudgeCaseList(judgeCaseList);
        judgeContext.setQuestion(question);
        judgeContext.setQuestionSubmit(questionSubmit);

        //沙箱只是单纯地执行代码，判题逻辑在此处实现，采用了策略模式
        JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);

        // 6）修改数据库中的判题结果
        questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));

        update = questionSubmitService.updateById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }

        QuestionSubmit questionSubmitResult = questionSubmitService.getById(questionId);

        return questionSubmitResult;
    }
}
