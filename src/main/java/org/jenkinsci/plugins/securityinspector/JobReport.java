/*
 * The MIT License
 *
 * Copyright 2014 Ksenia Nenasheva <ks.nenasheva@gmail.com>
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

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class JobReport extends PermissionReport<TopLevelItem,Boolean> {

    @Override
    protected Boolean getEntryReport(TopLevelItem column, Permission item) {
        Item i = getInstance().getItemByFullName(column.getFullName());
        if (i == null) { 
            // Item is not accessible anymore, so we cannot check it's permission
            return false;
        }
        return i.hasPermission(item);
    }
    
    @Nonnull
    @Restricted(NoExternalUse.class)
    public static Jenkins getInstance() throws IllegalStateException {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) {
            throw new IllegalStateException("Jenkins has not been started, or was already shut down");
        }
        return instance;
    }
    
     public final void generateReport(Set<TopLevelItem> rows) {
         Set<PermissionGroup> groups = new HashSet<PermissionGroup>(PermissionGroup.getAll());
         groups.remove(PermissionGroup.get(Permission.class));
         groups.remove(PermissionGroup.get(Hudson.class));
         groups.remove(PermissionGroup.get(Computer.class));
         groups.remove(PermissionGroup.get(View.class));
         
         super.generateReport(rows, groups);
     }
     
     public static JobReport createReport(Set<TopLevelItem> rows) {
         JobReport report = new JobReport();
         report.generateReport(rows);
         return report;
     }

    @Override
    public String getRowColumnHeader() {
        return Messages.JobReport_RowColumnHeader();
    }

    @Override
    public String getRowTitle(TopLevelItem row) {
        return row.getFullDisplayName();
    }

    @Override
    public boolean isEntryReportOk(TopLevelItem row, Permission item, Boolean report) {
        return report != null ? report.booleanValue() : false;
    }
}
