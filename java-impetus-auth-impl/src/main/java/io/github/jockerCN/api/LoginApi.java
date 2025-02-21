package io.github.jockerCN.api;

import io.github.jockerCN.Result;
import io.github.jockerCN.api.param.LoginRequest;
import io.github.jockerCN.common.SpringExecutorHandle;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.TransactionProvider;
import io.github.jockerCN.dao.DaoUtils;
import io.github.jockerCN.dao.UserAccount;
import io.github.jockerCN.dao.UserLoginInfo;
import io.github.jockerCN.dao.UserSettings;
import io.github.jockerCN.event.EventPush;
import io.github.jockerCN.event.UserPermissionEvent;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.jpa.autoRepository.JpaRepositoryUtils;
import io.github.jockerCN.log.AutoLog;
import io.github.jockerCN.secret.Cryption;
import io.github.jockerCN.token.TokenGenerate;
import io.github.jockerCN.token.TokenProcessException;
import io.github.jockerCN.token.TokenWrapper;
import io.github.jockerCN.token.process.TokenRecordProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@RequestMapping("/v1/login")
@RestController
public class LoginApi {


    @AutoLog(description = "user login model [password]")
    @PostMapping("/up")
    public Result<String> login(@RequestBody @Validated LoginRequest loginRequest) {

        Cryption instance = Cryption.getInstance(loginRequest.getClass());

        final String username;
        final String password;
        try {
            username = instance.decryptAsString(loginRequest.getUsername());
            password = instance.decryptAsString(loginRequest.getPassword());
        } catch (Exception e) {
            log.error("use [{}] decrypt failed:{}", instance.getClass(), e.getMessage(), e);
            return Result.failWithMsg("login failed: decrypt error");
        }

        UserAccount userAccount = UserAccount.getByUsername(username);

        if (Objects.isNull(userAccount)) {
            return Result.failWithMsg("account does not exist");
        }

        if (!userAccount.getPassword().equals(password)) {
            return Result.failWithMsg("password is incorrect");
        }

        String userCode = userAccount.getUserCode();
        UserSettings userSettings = DaoUtils.getUserSettingsByUserCode(userCode);

        boolean sso = false;
        if (Objects.nonNull(userSettings)) {
            sso = userSettings.isSsoEnabled();
        }

        TokenRecordProcess recordProcess = TokenRecordProcess.getInstance();
        final UserLoginInfo loginInfo = recordProcess.getUserLogin(userCode);
        //如果没有开启单点登录 则直接返回token
        if (Objects.nonNull(loginInfo) && !sso) {
            return Result.ok(loginInfo.getAccessToken());
        }

        SpringExecutorHandle executorHandle = SpringProvider.getBean(SpringExecutorHandle.class);

        Result<?> clearResult = executorHandle.executeThrows(() -> {
            try {
                recordProcess.clearTokenInfo(userCode);
                return Result.ok();
            } catch (TokenProcessException e) {
                TransactionProvider.setIfRollbackOnly();
                log.error("login failed,clear token info failed:{}", e.getMessage(), e);
                return Result.failWithMsg("clear token failed");
            }
        });

        if (clearResult.isError()) {
            return Result.failWithMsg(clearResult.getMessage());
        }


        TokenGenerate tokenGenerate = TokenGenerate.getInstance(UserAccount.class);
        if (Objects.isNull(tokenGenerate)) {
            log.error("[TokenGenerate] is null. no TokenGenerate support:[{}] ", UserAccount.class);
            return Result.failWithMsg("login failed,token generate error,no TokenGenerate support");
        }

        return executorHandle.executeThrows(() -> {
            TokenWrapper tokenWrapper;
            try {
                tokenWrapper = tokenGenerate.generate(userAccount);
                RequestContext.getRequestContext().setUserName(username);
                recordProcess.creator(tokenWrapper);
                userAccount.setLastLogin(tokenWrapper.tokenInfo().getCreateTime());
                JpaRepositoryUtils.save(userAccount);
            } catch (Exception e) {
                log.error("[TokenGenerate] generate error:[{}] ", e.getMessage(), e);
                TransactionProvider.setIfRollbackOnly();
                return Result.failWithMsg("login failed,token generate error");
            }
            TransactionProvider.doAfterCommit(() -> {
                log.info("### publish event UserPermissionEvent userCode:[{}] ###", userCode);
                EventPush.push(UserPermissionEvent.builder().userCode(userCode).build());
            });
            return Result.ok(tokenWrapper.tokenInfo().getToken());
        });
    }
}
