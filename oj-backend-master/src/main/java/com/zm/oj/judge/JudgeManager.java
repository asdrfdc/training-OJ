package com.zm.oj.judge;

import com.zm.oj.judge.strategy.DefaultJudgeStrategy;
import com.zm.oj.judge.strategy.JavaLanguageJudgeStrategy;
import com.zm.oj.judge.strategy.JudgeContext;
import com.zm.oj.judge.strategy.JudgeStrategy;
import com.zm.oj.judge.codesandbox.model.JudgeInfo;
import com.zm.oj.model.entity.QuestionSubmit;
import org.springframework.stereotype.Service;

/**
 * 判题管理（简化调用）
 */
@Service
public class JudgeManager {

    /**
     * 执行判题
     *
     * @param judgeContext
     * @return
     */
    JudgeInfo doJudge(JudgeContext judgeContext) {

        QuestionSubmit questionSubmit = judgeContext.getQuestionSubmit();
        String language = questionSubmit.getLanguage();

        JudgeStrategy judgeStrategy = new DefaultJudgeStrategy();
        if ("java".equals(language)) {
            judgeStrategy = new JavaLanguageJudgeStrategy();
        }
        return judgeStrategy.doJudge(judgeContext);
    }

}
