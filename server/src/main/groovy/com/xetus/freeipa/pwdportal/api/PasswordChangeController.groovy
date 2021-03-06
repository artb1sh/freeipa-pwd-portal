package com.xetus.freeipa.pwdportal.api;

import javax.servlet.http.HttpServletRequest

import org.springframework.http.ResponseEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import com.xetus.freeipa.pwdportal.ipa.change.ChangeService
import com.xetus.freeipa.pwdportal.notification.EmailNotificationService
import com.xetus.freeipa.pwdportal.recaptcha.RecaptchaService
import com.xetus.oss.iris.InvalidPasswordException
import com.xetus.oss.iris.InvalidUserOrRealmException
import com.xetus.oss.iris.PasswordExpiredException
import com.xetus.oss.iris.PasswordPolicyViolationException
import com.xetus.oss.iris.ProbableLockOutException

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j


import com.xetus.freeipa.pwdportal.model.PwPortalUser

@Slf4j
@CompileStatic
@RestController
public class PasswordChangeController {
  
  ChangeService changeService
  RecaptchaService recaptcha;
  
  @Autowired
  public PasswordResetController(ChangeService changeService,
                                 RecaptchaService recaptcha) {
    this.changeService = changeService
    this.recaptcha = recaptcha
  }
  
  @RequestMapping(value = "/api/password/{username}/change", method = RequestMethod.POST)
  public ResponseEntity<APIResponse> change(@PathVariable String username,
                                            @RequestBody ChangeRequest data,
                                            HttpServletRequest request) {
    log.info "Change password attempt received for user: $username"
    String requestIp = request.getRemoteAddr();
    if (!recaptcha.verifyRecaptcha(data.recaptchaResponse, requestIp)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(APIResponse.invalidRecaptcha())
    }
    
    try {
      PwPortalUser user = changeService.change(
        username,
        data.password,
        data.newPassword
      )
      log.trace "Returning success"
      return ResponseEntity.ok()
                           .body(new APIResponse("Successfully changed password"));
    } catch(PasswordExpiredException e) {
       log.info "$username supplied an expired password"
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                           .body(APIResponse.passwordExpired())
      
    } catch(PasswordPolicyViolationException e) {
       log.info "new password for $username violated password policy"
      return ResponseEntity.badRequest()
                           .body(APIResponse.policyViolation(e.getViolations()))
      
    } catch(InvalidPasswordException | InvalidUserOrRealmException e) {
       log.info "$username supplied invalid credentials"
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                           .body(APIResponse.invalidCredentials())
    
    } catch(ProbableLockOutException e) {
       log.info "$username is locked out or disabled"
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                           .body(APIResponse.lockedAccount());
    } catch (Exception e) {
      log.error "Encountered exception while changing password", e
      return ResponseEntity.status(500).body(APIResponse.serverError());
    }
  }
  
  @ToString(includeFields = true, includeNames = true)
  public static class ChangeRequest {
    String password
    String newPassword
    String recaptchaResponse
  }
}
