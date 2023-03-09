package com.tufitko.kafkafsjca;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import org.apache.kafka.common.security.plain.internals.PlainSaslServerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesystemLoginModule implements LoginModule {
  private static final Logger log = LoggerFactory.getLogger(FilesystemLoginModule.class);
  static final String USERNAME_KEY = "username";
  static final String PASSWORD_KEY = "password";

  static {
    PlainSaslServerProvider.initialize();
  }

  public FilesystemLoginModule() {}

  @Override
  public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
    String username = (String) options.get(USERNAME_KEY);
    String password = (String) options.get(PASSWORD_KEY);
    log.info("Initializing FilesystemLoginModule");

    Preconditions.checkArgument(!Strings.isNullOrEmpty(username), "Jaas file needs an entry %s", USERNAME_KEY);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(password), "Jaas file needs an entry %s", PASSWORD_KEY);

    subject.getPublicCredentials().add(username);
    subject.getPrivateCredentials().add(password);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean login() throws LoginException {
    log.debug("Login Called");
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean commit() throws LoginException {
    log.debug("Commit called");
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean abort() throws LoginException {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean logout() throws LoginException {
    return true;
  }
}
