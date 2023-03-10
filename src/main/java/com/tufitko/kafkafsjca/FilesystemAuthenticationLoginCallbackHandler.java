package com.tufitko.kafkafsjca;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;

import org.apache.kafka.common.security.JaasContext;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.plain.PlainAuthenticateCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Files;


public class FilesystemAuthenticationLoginCallbackHandler implements AuthenticateCallbackHandler {
  private static final Logger log = LoggerFactory.getLogger(FilesystemAuthenticationLoginCallbackHandler.class);
  static final String PATH = "path";
  private String path;

  public FilesystemAuthenticationLoginCallbackHandler() {}

  /**
   * {@inheritDoc}
   */
  @Override
  public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {
    // Loading path from jaas config
    path = JaasContext.configEntryOption(jaasConfigEntries, PATH, FilesystemLoginModule.class.getName());
    Preconditions.checkArgument(!Strings.isNullOrEmpty(path), "Jaas file needs an entry %s to the path where the users reside", PATH);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    log.debug("Close called");
  }

  /**
   * Handles callback, expects a {@link NameCallback} with the username and a {@link PlainAuthenticateCallback} with the password.
   * @param callbacks Callback array with NameCallback and PlainAuthenticateCallback
   * @throws UnsupportedCallbackException thrown when callbacks are not of either type expected.
   * @see CallbackHandler#handle(javax.security.auth.callback.Callback[])
   */
  @Override
  public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
    String username = null;
    for (Callback callback : callbacks) {
      if (callback instanceof NameCallback) {
        username = ((NameCallback) callback).getDefaultName();
        log.debug("Handling callback for NameCallback {}", username);
        continue;
      }

      if (callback instanceof PlainAuthenticateCallback) {
        log.debug("Handling callback for PlainAuthenticateCallback pwd length {}", ((PlainAuthenticateCallback) callback).password().length);
        PlainAuthenticateCallback plainCallback = (PlainAuthenticateCallback) callback;
        plainCallback.authenticated(authenticate(username, plainCallback.password()));
        continue;
      }

      throw new UnsupportedCallbackException(callback);
    }
  }

  private boolean authenticate(String username, char[] password) throws IOException {
    if (username == null) {
      return false;
    }

    File file = new File(path); 
    List<String> lines = Files.asCharSource(file, Charsets.UTF_8).readLines();

    for (int i = 0; i < lines.size(); i++) {
      List<String> tokens = Splitter.on(":").trimResults().omitEmptyStrings().splitToList(lines.get(i));
      if (tokens.size() != 2) {
        continue;
      }
      if (tokens.get(0).equals(username) && Arrays.equals(tokens.get(1).toCharArray(), password)) {
        log.info("authorize {}", username);
        return true;
      }
    }
    
    return false;
  }
}
