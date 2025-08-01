package io.github.jockerCN.api;

import io.github.jockerCN.Result;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.http.request.RequestInfo;
import io.github.jockerCN.log.AutoLog;
import io.github.jockerCN.permissions.UserPermissionsProcess;
import io.github.jockerCN.token.process.TokenRecordProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@RequestMapping("/v1/logout")
@RestController
public class LogoutApi {


    @AutoLog("user logout")
    @PostMapping("/out")
    public Result<Void> login() {
        RequestInfo requestInfo = RequestContext.getRequestContext();
        String userCode = requestInfo.userInfo().getUserCode();
        TokenRecordProcess.getInstance().clearTokenInfo(userCode);
        UserPermissionsProcess.getInstance().remove(userCode);
        return Result.ok();
    }
}
