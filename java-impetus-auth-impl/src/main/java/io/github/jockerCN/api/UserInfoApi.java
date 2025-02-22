package io.github.jockerCN.api;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import io.github.jockerCN.Result;
import io.github.jockerCN.api.param.*;
import io.github.jockerCN.common.TransactionProvider;
import io.github.jockerCN.dao.*;
import io.github.jockerCN.dao.query.*;
import io.github.jockerCN.event.EventPush;
import io.github.jockerCN.event.UserLogOutEvent;
import io.github.jockerCN.event.UserPermissionEvent;
import io.github.jockerCN.generator.SnowflakeIdGenerator;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.jpa.autoRepository.JpaRepositoryUtils;
import io.github.jockerCN.jpa.rep.UserPermissionsRep;
import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.log.AutoLog;
import io.github.jockerCN.secret.Cryption;
import io.github.jockerCN.secret.CryptoUtils;
import io.github.jockerCN.stream.StreamUtils;
import io.github.jockerCN.token.process.TokenRecordProcess;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@RequestMapping("/v1/userInfo")
@RestController
public class UserInfoApi {

    @Autowired
    private UserPermissionsRep userPermissionsRep;

    @PostMapping("saveUserPermission")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> saveUserPermission(@RequestBody @Validated UserPermissionSave userPermissionSave) {

        Set<PermissionInfoVO> permissionInfos = userPermissionSave.getPermissionInfos();
        //清理所有权限
        userPermissionsRep.deleteUserPermissions(userPermissionSave.getUserCode());
        if (CollectionUtils.isNotEmpty(permissionInfos)) {
            List<UserPermissions> userPermissions = new ArrayList<>(permissionInfos.size());
            for (PermissionInfoVO permissionInfo : permissionInfos) {
                userPermissions.add(UserPermissions.builder()
                        .userId(userPermissionSave.getUserCode())
                        .userPermissionId(SnowflakeIdGenerator.getInstance().nextIdAsString("UP"))
                        .permissionId(permissionInfo.getPermissionId())
                        .accessLevel(permissionInfo.getAccessLevel())
                        .build());
            }

            if (CollectionUtils.isNotEmpty(userPermissions)) {
                JpaRepositoryUtils.saveAll(userPermissions, UserPermissions.class);
            }
        }

        TransactionProvider.doAfterCommit(() -> {
            log.info("### publish event UserPermissionEvent userCode:[{}] add permission ###", userPermissionSave.getUserCode());
            EventPush.push(UserPermissionEvent.builder().userCode(userPermissionSave.getUserCode()).build());
        });
        return Result.ok();
    }


    @GetMapping("geUserBaseInfo")
    public Result<List<UserBaseInfo>> geUserBaseInfo(@RequestParam("username") String username) {
        final UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setUsernameLike("%" + username + "%");
        List<UserAccount> accountList = DaoUtils.getUserAccountList(queryParam);
        return Result.ok(StreamUtils.toList(accountList, (account) -> UserBaseInfo.builder().userCode(account.getUserCode())
                .username(account.getUsername())
                .build()));
    }

    @GetMapping("geUserPermission")
    public Result<Set<String>> geUserPermission(@RequestParam("userCode") String userCode) {
        final UserPermissionsQueryParam queryParam = new UserPermissionsQueryParam();
        queryParam.setUserId(userCode);
        List<UserPermissions> userPermissions = DaoUtils.getUserPermissionsList(queryParam);
        return Result.ok(StreamUtils.toSet(userPermissions, UserPermissions::getPermissionId));
    }

    @GetMapping("getProfile")
    public Result<UserProfile> getUserProfile(@RequestParam("userCode") @NotBlank(message = "用户编码为空") String userCode) {
        UserProfileQueryParam queryParam = new UserProfileQueryParam();
        queryParam.setUserCode(userCode);
        return Result.ok(DaoUtils.getUserProfile(queryParam));
    }

    @PostMapping("resetPwd")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> resetPwd(@RequestParam("userCode") @NotBlank(message = "用户编码为空") String userCode) {
        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setUserCode(userCode);
        UserAccount userAccount = DaoUtils.getUserAccount(queryParam);
        if (Objects.isNull(userAccount)) {
            return Result.failWithMsg("未查询到用户信息");
        }

        userAccount.setPassword(CryptoUtils.md5(userAccount.getUsername()));

        JpaRepositoryUtils.save(userAccount);

        TransactionProvider.doAfterCommit(() -> {
            log.info("### publish user offline event [{}]: password reset  ###", userAccount.getUserCode());
            EventPush.push(UserLogOutEvent.builder().userCode(userAccount.getUserCode()).build());
        });
        return Result.ok();
    }


    @PostMapping("updateAccountInfo")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateAccountInfo(@RequestBody @Validated UserAccountAddOrUpdate userAccountAddOrUpdate) {
        final Long id = userAccountAddOrUpdate.getId();
        if (Objects.isNull(id)) {
            return Result.failWithMsg("修改id为空");
        }

        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setId(id);
        UserAccount userAccount = DaoUtils.getUserAccount(queryParam);
        if (Objects.isNull(userAccount)) {
            return Result.failWithMsg("未查询到用户账户");
        }

        userAccount.setEmail(userAccountAddOrUpdate.getEmail());
        userAccount.setPhone(userAccountAddOrUpdate.getPhone());
        userAccount.setIdCardNumber(userAccountAddOrUpdate.getIdCardNumber());
        userAccount.setStatus(userAccountAddOrUpdate.getStatus());
        userAccount.setUserType(userAccountAddOrUpdate.getUserType());

        JpaRepositoryUtils.save(userAccount);

        UserProfileQueryParam profileQueryParam = new UserProfileQueryParam();
        profileQueryParam.setUserCode(userAccount.getUserCode());
        UserProfile userProfile = DaoUtils.getUserProfile(profileQueryParam);
        if (Objects.nonNull(userProfile)) {
            userProfile.setFullName(userAccountAddOrUpdate.getFullName());
            userProfile.setGender(userAccountAddOrUpdate.getGender());
            userProfile.setBirthdate(userAccountAddOrUpdate.getBirthdate());
            userProfile.setLocale(userAccountAddOrUpdate.getLocale());
            userProfile.setTimezone(userAccountAddOrUpdate.getTimezone());
            userProfile.setIdCardNumber(Strings.nullToEmpty(userAccountAddOrUpdate.getIdCardNumber()));
            userProfile.setIdCardName(Strings.nullToEmpty(userAccountAddOrUpdate.getIdCardName()));
            userProfile.setIdCardIssuedDate(userAccountAddOrUpdate.getIdCardIssuedDate());
            userProfile.setIdCardExpiration(userAccountAddOrUpdate.getIdCardExpiration());
            userProfile.setAddress(Strings.nullToEmpty(userAccountAddOrUpdate.getAddress()));
            JpaRepositoryUtils.save(userProfile);
        }

        TransactionProvider.doAfterCommit(() -> {
            switch (userAccount.getStatus()) {
                case LOCKED, SUSPENDED:
                    log.info("### publish user offline event [{}] status: [{}] ###", userAccount.getUserCode(), userAccount.getStatus());
                    EventPush.push(UserLogOutEvent.builder().userCode(userAccount.getUserCode()).build());
            }
        });
        return Result.ok();
    }

    @PostMapping("addAccountInfo")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addAccountInfo(@RequestBody @Validated UserAccountAddOrUpdate userAccountAddOrUpdate) {
        final String username = userAccountAddOrUpdate.getUsername();

        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setUsername(username);
        UserAccount userAccount = DaoUtils.getUserAccount(queryParam);
        if (Objects.nonNull(userAccount)) {
            return Result.failWithMsg("用户名重复");
        }
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.getInstance();
        String userCode = generator.nextIdAsString("U");

        JpaRepositoryUtils.save(UserAccount.builder()
                .userCode(userCode)
                .username(userAccountAddOrUpdate.getUsername())
                .email(userAccountAddOrUpdate.getEmail())
                .phone(userAccountAddOrUpdate.getPhone())
                .idCardNumber(Strings.nullToEmpty(userAccountAddOrUpdate.getIdCardNumber()))
                .algorithmCode("")
                .password(CryptoUtils.md5(userAccountAddOrUpdate.getUsername()))
                .status(userAccountAddOrUpdate.getStatus())
                .lastLogin(0L)
                .failedLoginAttempts(0)
                .lockedUntil(0L)
                .userType(userAccountAddOrUpdate.getUserType())
                .build());

        JpaRepositoryUtils.save(UserProfile.builder()
                .userCode(userCode)
                .fullName(userAccountAddOrUpdate.getFullName())
                .gender(userAccountAddOrUpdate.getGender())
                .birthdate(userAccountAddOrUpdate.getBirthdate())
                .locale(Strings.nullToEmpty(userAccountAddOrUpdate.getLocale()))
                .timezone(Strings.nullToEmpty(userAccountAddOrUpdate.getTimezone()))
                .profileUrl("")
                .idCardNumber(Strings.nullToEmpty(userAccountAddOrUpdate.getIdCardNumber()))
                .idCardName(Strings.nullToEmpty(userAccountAddOrUpdate.getIdCardName()))
                .idCardIssuedDate(userAccountAddOrUpdate.getIdCardIssuedDate())
                .idCardExpiration(userAccountAddOrUpdate.getIdCardExpiration())
                .address(Strings.nullToEmpty(userAccountAddOrUpdate.getAddress()))
                .build());

        JpaRepositoryUtils.save(UserGroups.builder()
                .userId(userCode)
                .username(userAccountAddOrUpdate.getUsername())
                .groupsId(GsonUtils.toJson(Sets.newHashSet()))
                .build()
        );


        JpaRepositoryUtils.save(UserSettings.builder()
                .userCode(userCode)
                .username(userAccountAddOrUpdate.getUsername())
                .ssoEnabled(false)
                .twoFaEnabled(false)
                .twoFaMethod("")
                .build()
        );


        return Result.ok();
    }

    @GetMapping("profile")
    public Result<UserProfile> getUserProfile() {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isBlank(userCode)) {
            return Result.failWithMsg("certificate is empty");
        }

        UserProfileQueryParam queryParam = new UserProfileQueryParam();
        queryParam.setUserCode(userCode);
        return Result.ok(DaoUtils.getUserProfile(queryParam));
    }

    @PostMapping("updateAccount")
    public Result<UserAccount> updateUserAccount(@RequestBody UserAccountUpdate userAccountUpdate) {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isBlank(userCode)) {
            return Result.failWithMsg("certificate is empty");
        }
        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setUserCode(userCode);
        UserAccount userAccount = DaoUtils.getUserAccount(queryParam);

        if (Objects.isNull(userAccount)) {
            return Result.failWithMsg("未查询到用户信息");
        }
        final String username = userAccountUpdate.getUsername();

        //修改用户名
        if (!userAccount.getUsername().equals(username)) {
            queryParam = new UserAccountQueryParam();
            queryParam.setUsername(username);
            UserAccount otherAccount = DaoUtils.getUserAccount(queryParam);
            if (Objects.nonNull(otherAccount)) {
                return Result.failWithMsg("用户名已存在");
            }
        }
        userAccount.setEmail(userAccountUpdate.getEmail());
        userAccount.setPhone(userAccountUpdate.getPhone());
        userAccount.setUsername(userAccountUpdate.getUsername());
        JpaRepositoryUtils.save(userAccount);
        return Result.ok();
    }

    @AutoLog(description = "change password")
    @PostMapping("upPwd")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> changePwd(@RequestBody PasswordUpdate passwordUpdate) {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isBlank(userCode)) {
            return Result.failWithMsg("certificate is empty");
        }

        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setUserCode(userCode);
        UserAccount userAccount = DaoUtils.getUserAccount(queryParam);

        if (Objects.isNull(userAccount)) {
            return Result.failWithMsg("用户不存在");
        }

        final String originalPassword = passwordUpdate.getOriginalPassword();
        final String newPassword = passwordUpdate.getNewPassword();

        try {
            String originalDePwd = Cryption.getInstance(PasswordUpdate.class).decryptAsString(originalPassword);
            String newDePwd = Cryption.getInstance(PasswordUpdate.class).decryptAsString(newPassword);
            if (!userAccount.getPassword().equals(originalDePwd)) {
                return Result.failWithMsg("原密码错误");
            }
            userAccount.setPassword(newDePwd);
            JpaRepositoryUtils.save(userAccount);
            TransactionProvider.doAfterCommit(() -> {
                log.info("### publish user offline event [{}] user change password", userAccount.getUserCode());
                EventPush.push(UserLogOutEvent.builder().userCode(userAccount.getUserCode()).build());
            });
            return Result.ok();
        } catch (Exception e) {
            log.error("user:{} update pwd failed:{}", userCode, e.getMessage());
            return Result.failWithMsg("修改密码失败");
        }
    }

    @GetMapping("settings")
    public Result<UserSettings> getSettings() {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isBlank(userCode)) {
            return Result.failWithMsg("certificate is empty");
        }
        UserSettingsQueryParam queryParam = new UserSettingsQueryParam();
        queryParam.setUserCode(userCode);
        return Result.ok(DaoUtils.getUserSettings(queryParam));
    }

    @PostMapping("setSSO")
    public Result<Void> setSSO(@RequestParam("enable") boolean enable) {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isBlank(userCode)) {
            return Result.failWithMsg("certificate is empty");
        }
        UserSettingsQueryParam queryParam = new UserSettingsQueryParam();
        queryParam.setUserCode(userCode);
        UserSettings settings = DaoUtils.getUserSettings(queryParam);
        settings.setSsoEnabled(enable);
        JpaRepositoryUtils.save(settings);
        return Result.ok();
    }

    @GetMapping("accountInfo")
    public Result<UserAccount> getAccountInfo() {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isBlank(userCode)) {
            return Result.failWithMsg("certificate is empty");
        }

        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setUserCode(userCode);
        UserAccount userAccount = DaoUtils.getUserAccount(queryParam);
        userAccount.setPassword("");
        userAccount.setUserCode("");
        userAccount.setUpdateTime(null);
        userAccount.setCreator("");
        userAccount.setUpdater("");
        userAccount.setAlgorithmCode("");
        userAccount.setIdCardNumber("");
        return Result.ok(userAccount);
    }


    @PostMapping("update")
    public Result<UserProfile> updateUserProfile(@RequestBody UserProfileUpdate userProfileUpdate) {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isBlank(userCode)) {
            return Result.failWithMsg("certificate is empty");
        }

        UserProfileQueryParam queryParam = new UserProfileQueryParam();
        queryParam.setUserCode(userCode);
        UserProfile userProfile = DaoUtils.getUserProfile(queryParam);
        if (Objects.isNull(userProfile)) {
            return Result.failWithMsg("未查询到用户信息");
        }
        userProfile.setFullName(Strings.nullToEmpty(userProfileUpdate.getFullName()));
        userProfile.setGender(userProfileUpdate.getGender());
        userProfile.setBirthdate(userProfileUpdate.getBirthdate().toLocalDate());
        userProfile.setAddress(Strings.nullToEmpty(userProfileUpdate.getAddress()));
        JpaRepositoryUtils.save(userProfile);
        return Result.ok(userProfile);
    }

    @PostMapping("delUserInfo")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delUserInfo(@RequestBody @Validated UserAccountDelVo userAccountDelVo) {
        UserAccountQueryParam queryParam = new UserAccountQueryParam();
        queryParam.setId(userAccountDelVo.getId());
        UserAccount userAccount = DaoUtils.getUserAccount(queryParam);
        if (Objects.isNull(userAccount)) {
            return Result.failWithMsg("未查询到账户信息");
        }


        //用户下线
        String userCode = userAccount.getUserCode();
        TokenRecordProcess.getInstance().clearTokenInfo(userCode);

        //清理登录配置 个人信息配置

        UserSettingsQueryParam settingsQueryParam = new UserSettingsQueryParam();
        settingsQueryParam.setUserCode(userCode);
        UserSettings settings = DaoUtils.getUserSettings(settingsQueryParam);

        if (Objects.nonNull(settings)) {
            JpaRepositoryUtils.delete(settings);
        }

        UserProfileQueryParam profileQueryParam = new UserProfileQueryParam();
        profileQueryParam.setUserCode(userCode);
        UserProfile userProfile = DaoUtils.getUserProfile(profileQueryParam);
        if (Objects.nonNull(userProfile)) {
            JpaRepositoryUtils.delete(userProfile);
        }

        //删除独立权限配置
        userPermissionsRep.deleteUserPermissions(userCode);

        //删除组信息
        UserGroupsQueryParam userGroupsQueryParam = new UserGroupsQueryParam();
        userGroupsQueryParam.setUserId(userCode);

        UserGroups userGroups = DaoUtils.getUserGroups(userGroupsQueryParam);
        if (Objects.nonNull(userGroups)) {
            JpaRepositoryUtils.delete(userGroups);
        }

        //删除账户信息
        JpaRepositoryUtils.delete(userAccount);

        return Result.ok();
    }
}
