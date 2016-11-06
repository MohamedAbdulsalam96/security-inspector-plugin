/*
 * The MIT License
 *
 * Copyright 2014-2016 Ksenia Nenasheva <ks.nenasheva@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.securityinspector;

import org.jenkinsci.plugins.securityinspector.util.JobFilter;
import org.jenkinsci.plugins.securityinspector.util.ComputerFilter;
import org.jenkinsci.plugins.securityinspector.util.UserFilter;
import org.jenkinsci.plugins.securityinspector.impl.users.permissionsForComputers.PermissionsForUsersReport;
import org.jenkinsci.plugins.securityinspector.impl.users.permissionsForJobs.UserReport;
import org.jenkinsci.plugins.securityinspector.impl.jobs.permissionsForUsers.JobReport;
import org.jenkinsci.plugins.securityinspector.model.SecurityInspectorReport;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AllView;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ManagementLink;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.model.View;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.securityinspector.model.ReportBuilder;
import static org.jenkinsci.plugins.securityinspector.model.ReportBuilder.all;
import org.jenkinsci.plugins.securityinspector.util.JenkinsHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;

@Extension
public class SecurityInspectorAction extends ManagementLink {

  private static final SecurityInspectorHelper helper = new SecurityInspectorHelper();
  
  @Nonnull
  transient UserContextCache contextMap;

  public SecurityInspectorAction() {
    this.contextMap = new UserContextCache();
  }

  @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
          justification = "This method resolves data loaded from disk. This data bypasses constructor in XStream")
  protected Object readResolve() {
    if (contextMap == null) {
     contextMap = new UserContextCache();
     }
    return this;
  }
  
  @Override
  public String getIconFileName() {
    return "secure.gif";
  }

  @Override
  public String getDisplayName() {
    return "Security Inspector";
  }

  @Override
  public String getDescription() {
    return "Inspect permissions configured by Jenkins security settings";
  }

  @Override
  public String getUrlName() {
    return "security-inspector";
  }

  /**
   * Retrieves a helper class for the action UI.
   * @return Instance of the helper
   */
  @Nonnull
  @Restricted(NoExternalUse.class)
  public SecurityInspectorHelper getHelper() {
    return helper;
  }

  //TODO: fix rawtype before the release
  @Nonnull
  @Restricted(NoExternalUse.class)
  public SecurityInspectorReport getReportJob() {
    Set<TopLevelItem> items = getRequestedJobs();
    User user = getRequestedUser();
    final JobReport report;

        // Impersonate to check the permission
    final Authentication auth;
    try {
      auth = user.impersonate();
    } catch (UsernameNotFoundException ex) {
      return new JobReport();
    }

    //TODO: rework the logic to guarantee that report is initialized
    SecurityContext initialContext = null;
    try {
      initialContext = hudson.security.ACL.impersonate(auth);
      report = JobReport.createReport(items);
    } finally {
      if (initialContext != null) {
        SecurityContextHolder.setContext(initialContext);
      }
    }
    return report;
  }

  //TODO: fix rawtype before the release
  @Nonnull
  @Restricted(NoExternalUse.class)
  public SecurityInspectorReport getReportUser() {
    Set<User> users = getRequestedUsers();
    Item job = getRequestedJob();

    UserReport report = UserReport.createReport(users, job);
    return report;
  }

  //TODO: Rename Slave => Node
  @Nonnull
  @Restricted(NoExternalUse.class)
  public SecurityInspectorReport getReportSlave() {
    Set<Computer> computers = getRequestedSlaves();
    Set<Computer> slaves = new HashSet<Computer>();
    for (Computer c : computers) {
      Node slave = c.getNode();
      if (slave != null) {
        slaves.add(c);
      }
    }

    User user = getRequestedUser();
    PermissionsForUsersReport report;

    // Impersonate to check the permission
    final Authentication auth;
    try {
      auth = user.impersonate();
    } catch (UsernameNotFoundException ex) {
      return new PermissionsForUsersReport();
    }

    SecurityContext initialContext = null;
    try {
      initialContext = hudson.security.ACL.impersonate(auth);
      report = PermissionsForUsersReport.createReport(slaves);
    } finally {
      if (initialContext != null) {
        SecurityContextHolder.setContext(initialContext);
      }
    }

    return report;
  }

  @CheckForNull
  private AllView getAllView() {
    for (View view : JenkinsHelper.getInstanceOrFail().getViews()) {
      if (view instanceof AllView) {
        return (AllView)view;
      }
    }
    return null;
  }

  @Nonnull
  @Restricted(NoExternalUse.class)
  public HttpResponse doFilterSubmit(@Nonnull StaplerRequest req) 
          throws ServletException, Descriptor.FormException {
    final Jenkins jenkins = JenkinsHelper.getInstanceOrFail();
    jenkins.checkPermission(Jenkins.ADMINISTER);
    
    String selectedItem;
    String valid;
    StringBuilder b = new StringBuilder();
    SubmittedOperation action = SubmittedOperation.fromRequest(req);

    switch (action) {
      case Submit4jobs:
        valid = req.getParameter("_.includeRegex");
        try {
          Pattern.compile(valid);
        } catch (PatternSyntaxException exception) {
          return HttpResponses.redirectTo(jenkins.getRootUrl() + "security-inspector/error");
        }
        selectedItem = req.getParameter("selectedUser");
        b.append("search_report_user_4_job");
        JobFilter filters = new JobFilter(req);
        updateSearchCache(filters, selectedItem);
        break;

      case Submit4slaves:
        valid = req.getParameter("_.includeRegex4Slave");
        try {
          Pattern.compile(valid);
        } catch (PatternSyntaxException exception) {
          return HttpResponses.redirectTo(jenkins.getRootUrl() + "security-inspector/error");
        }
        selectedItem = req.getParameter("selectedUser");
        b.append("search_report_user_4_slave");
        ComputerFilter filter4slave = new ComputerFilter(req);
        updateSearchCache(filter4slave, selectedItem);
        break;

      case Submit4user:
        valid = req.getParameter("_.includeRegex4User");
        try {
          Pattern.compile(valid);
        } catch (PatternSyntaxException exception) {
          return HttpResponses.redirectTo(jenkins.getRootUrl() + "security-inspector/error");
        }
        selectedItem = req.getParameter("selectedJobs");
        b.append("search_report_job");
        UserFilter filter4user = new UserFilter(req);
        updateSearchCache(filter4user, selectedItem);
        break;

      case GoToHP:
        return HttpResponses.redirectTo(jenkins.getRootUrl() + "security-inspector");

      default:
        throw new Descriptor.FormException("Action " + action + " is not supported", "submit");
    }

    // Redirect to the search report page
    String request = b.toString();
    return HttpResponses.redirectTo(request);
  }

  @Restricted(NoExternalUse.class)
  public void doGoHome(@Nonnull StaplerRequest req, @Nonnull StaplerResponse rsp) 
          throws IOException, ServletException, Descriptor.FormException {
    JenkinsHelper.getInstanceOrFail().checkPermission(Jenkins.ADMINISTER);
    GoHomeDestination action = GoHomeDestination.fromRequest(req);
    switch (action) {
      case GoToJF:
        rsp.sendRedirect("job-filter");
        break;
      case GoToSF:
        rsp.sendRedirect("slave-filter");
        break;
      case GoToUF:
        rsp.sendRedirect("user-filter");
        break;
      default:
        throw new IOException("Action " + action + " is not supported");
    }
  }
  
    @Nonnull
    @Restricted(NoExternalUse.class)
    public List<ReportBuilder> getReportBuilders(@Nonnull String type) {
        //TODO: Illegal argument handling
        return ReportBuilder.all(ReportBuilder.Type.valueOf(type));
    }

  /**
   * Get Jobs/Slaves/Users from the context
   * @return res List of the requested jobs
   */
  @Nonnull
  @Restricted(NoExternalUse.class)
  public Set<TopLevelItem> getRequestedJobs() throws HttpResponses.HttpResponseException {
    UserContext context = contextMap.get(getSessionId());
    if (context == null) {
      // TODO: 
      throw HttpResponses.error(404, "Context has not been found");
    }
    
    final JobFilter jobfilter = context.getJobFilter();
    if (jobfilter == null) {
        throw HttpResponses.error(500, "The retrieved context does not contain job filter settings");
    }
    
    // TODO: Ideally the plugin should not depend on the AllView existense
    final AllView sourceView = getAllView();
    if (sourceView == null) {
        throw HttpResponses.error(404, "Cannot find the All view in the Jenkins root");
    }
       
    List<TopLevelItem> selectedJobs = jobfilter.doFilter(sourceView);
    final Set<TopLevelItem> res = new HashSet<>(selectedJobs.size());
    for (TopLevelItem item : selectedJobs) {
      if (item != null) {
        res.add(item);
      }
    }
    return res;
  }

  @Nonnull
  @Restricted(NoExternalUse.class)
  public Set<Computer> getRequestedSlaves() throws HttpResponses.HttpResponseException {
    UserContext context = contextMap.get(getSessionId());
    if (context == null) {
      // TODO:  What todo?
      throw HttpResponses.error(404, "Context has not been found");
    }
    
    final ComputerFilter slaveFilter = context.getSlaveFilter();
    if (slaveFilter == null) {
        throw HttpResponses.error(500, "The retrieved context does not contain slave filter settings");
    }
    
    List<Computer> selectedSlaves = slaveFilter.doFilter();
    final Set<Computer> res = new HashSet<>(selectedSlaves.size());
    for (Computer item : selectedSlaves) {
      if (item != null) {
        res.add(item);
      }
    }
    return res;
  }

  @Nonnull
  @Restricted(NoExternalUse.class)
  public Set<User> getRequestedUsers() throws HttpResponses.HttpResponseException {
    UserContext context = contextMap.get(getSessionId());
    if (context == null) {
      // TODO: 
      throw HttpResponses.error(404, "Context has not been found");
    }
    
    final UserFilter userFilter = context.getUserFilter();
    if (userFilter == null) {
        throw HttpResponses.error(500, "The retrieved context does not contain user filter settings");
    }

    List<User> selectedUsers = userFilter.doFilter();
    final Set<User> res = new HashSet<>(selectedUsers.size());
    for (User item : selectedUsers) {
      if (item != null) {
        res.add(item);
      }
    }
    return res;
  }

  /**
   * Get selected user/job from context
   * @return user if exists. Otherwise an error will be returned
   */
  @Nonnull
  @Restricted(NoExternalUse.class)
  public User getRequestedUser() throws HttpResponses.HttpResponseException {
    UserContext context = contextMap.get(getSessionId());
    if (context == null) {
      // TODO: 
      throw HttpResponses.error(404, "Context hae not been found");
    }
    String userId = context.getItem();
    User user = User.get(userId, false, null);
    if (user == null) {
      throw HttpResponses.error(404, "User " + userId + " does not exist");
    }
    return user;
  }

  @Nonnull
  @Restricted(NoExternalUse.class)
  public Item getRequestedJob() throws HttpResponses.HttpResponseException {
    UserContext context = contextMap.get(getSessionId());
    if (context == null) {
      // TODO: 
      throw HttpResponses.error(404, "Context has not been found");
    }
    String jobName = context.getItem();
    Item job = JenkinsHelper.getInstanceOrFail().getItemByFullName(jobName, Item.class);
    if (job == null) {
      throw HttpResponses.error(404, "Job " + jobName + " does not exist");
    }
    return job;
  }

  /**
   * Buttons: - Submit Jobs/Slaves/Users reports - Go to Home Page
   */
  enum SubmittedOperation {

    Submit4jobs,
    Submit4slaves,
    Submit4user,
    GoToHP;

    
    /**
     * Locates the operation in the submitted form.
     * @param req Request
     * @return Located operation
     * @throws Descriptor.FormException Cannot find any command field from the enum
     */
    @Nonnull
    static SubmittedOperation fromRequest(@Nonnull StaplerRequest req) throws Descriptor.FormException {
      final Map<?, ?> map = req.getParameterMap();
      for (SubmittedOperation val : SubmittedOperation.values()) {
        if (map.containsKey(val.toString())) {
          return val;
        }
      }
      throw new Descriptor.FormException("Cannot find an action in the request", "submit");
    }
  }

  /**
   * Buttons: Go to page with filters
   */
  enum GoHomeDestination {

    GoToJF,
    GoToSF,
    GoToUF;

    /**
     * Locates the operation in the submitted form.
     * @param req Request
     * @return Located operation
     * @throws Descriptor.FormException Cannot find any return page destination field from the enum
     */
    @Nonnull
    static GoHomeDestination fromRequest(StaplerRequest req) throws Descriptor.FormException {
      final Map<?, ?> map = req.getParameterMap();
      for (GoHomeDestination val : GoHomeDestination.values()) {
        if (map.containsKey(val.toString())) {
          return val;
        }
      }
      throw new Descriptor.FormException("Cannot find an action in the request", "submit");
    }
  }

  //TODO: Handle IllegalStateException ?
  /**
   * Gets identifier of the current session.
   * @return Unique id of the current session.
   * @exception IllegalStateException if this method is called on an
   * invalidated session
   */
  @Nonnull
  public static String getSessionId() throws IllegalStateException {
    return Stapler.getCurrentRequest().getSession().getId();
  }

  public boolean hasConfiguredFilters() {
    return contextMap.containsKey(getSessionId());
  }

  /**
   * Cleans internal cache of JSON Objects for the session.
   * @return Current Session Id
   */
  @Nonnull
  public String cleanCache() {
    final String sessionId = getSessionId();
    contextMap.flush(sessionId);
    return sessionId;
  }

  public void updateSearchCache(@Nonnull JobFilter jobFilter, @Nonnull String item) {
    cleanCache();
    // Put Context to the map
    contextMap.put(getSessionId(), new UserContext(jobFilter, item));
  }

  public void updateSearchCache(@Nonnull ComputerFilter slaveFilter, @Nonnull String item) {
    cleanCache();
    // Put Context to the map
    contextMap.put(getSessionId(), new UserContext(slaveFilter, item));
  }

  public void updateSearchCache(@Nonnull UserFilter userFilter, @Nonnull String item) {
    cleanCache();
    // Put Context to the map
    contextMap.put(getSessionId(), new UserContext(userFilter, item));
  }
}
