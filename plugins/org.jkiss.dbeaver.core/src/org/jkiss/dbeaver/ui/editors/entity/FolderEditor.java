/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2016 Serge Rieder (serge@jkiss.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (version 2)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jkiss.dbeaver.ui.editors.entity;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.part.EditorPart;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.core.DBeaverUI;
import org.jkiss.dbeaver.model.navigator.DBNContainer;
import org.jkiss.dbeaver.model.navigator.DBNLocalFolder;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.navigator.DBNResource;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.ui.*;
import org.jkiss.dbeaver.ui.controls.itemlist.ItemListControl;
import org.jkiss.dbeaver.ui.editors.INavigatorEditorInput;
import org.jkiss.dbeaver.ui.navigator.INavigatorModelView;

import java.util.ArrayList;
import java.util.List;

/**
 * FolderEditor
 */
public class FolderEditor extends EditorPart implements INavigatorModelView, IRefreshablePart, ISearchContextProvider
{
    private static final Log log = Log.getLog(FolderEditor.class);

    private FolderListControl itemControl;
    private List<String> history = new ArrayList<>();
    private int historyPosition = 0;

    @Override
    public void createPartControl(Composite parent)
    {
        itemControl = new FolderListControl(parent);
        itemControl.createProgressPanel();
        itemControl.loadData();
        getSite().setSelectionProvider(itemControl.getSelectionProvider());
    }

    @Override
    public void setFocus() {
        itemControl.setFocus();
    }

    @Override
    public void doSave(IProgressMonitor monitor) {

    }

    @Override
    public void doSaveAs() {

    }

    @Override
    public INavigatorEditorInput getEditorInput() {
        return (INavigatorEditorInput) super.getEditorInput();
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        setSite(site);
        setInput(input);
        if (input != null) {
            final DBNNode navigatorNode = getEditorInput().getNavigatorNode();
            setTitleImage(DBeaverIcons.getImage(navigatorNode.getNodeIcon()));
            setPartName(navigatorNode.getNodeName());
        }
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public DBNNode getRootNode() {
        return getEditorInput().getNavigatorNode();
    }

    @Nullable
    @Override
    public Viewer getNavigatorViewer()
    {
        return itemControl.getNavigatorViewer();
    }

    @Override
    public void refreshPart(Object source, boolean force)
    {
        DBeaverUI.asyncExec(new Runnable() {
            @Override
            public void run() {
                if (!itemControl.isDisposed()) {
                    itemControl.loadData(false);
                }
            }
        });
    }

    @Override
    public boolean isSearchPossible()
    {
        return itemControl.isSearchPossible();
    }

    @Override
    public boolean isSearchEnabled()
    {
        return itemControl.isSearchEnabled();
    }

    @Override
    public boolean performSearch(SearchType searchType)
    {
        return itemControl.performSearch(searchType);
    }

    public int getHistoryPosition() {
        return historyPosition;
    }

    public int getHistorySize() {
        return history.size();
    }

    public void navigateHistory(int offset) {
        historyPosition += offset;
        if (historyPosition >= history.size()) {
            historyPosition = history.size() - 1;
        } else if (historyPosition < 0) {
            historyPosition = -1;
        }
        if (historyPosition <0 || historyPosition >= history.size()) {
            return;
        }
        String nodePath = history.get(historyPosition);
        try {
            DBNNode node = DBeaverCore.getInstance().getNavigatorModel().getNodeByPath(VoidProgressMonitor.INSTANCE, nodePath);
            if (node != null) {
                itemControl.changeCurrentNode(node);
            }
        } catch (DBException e) {
            log.error(e);
        }

    }

    private class FolderListControl extends ItemListControl {
        public FolderListControl(Composite parent) {
            super(parent, SWT.SHEET, FolderEditor.this.getSite(), FolderEditor.this.getEditorInput().getNavigatorNode(), null);
        }

        @Override
        protected void openNodeEditor(DBNNode node) {
            final DBNNode rootNode = getRootNode();
            if ((rootNode instanceof DBNContainer && node instanceof DBNLocalFolder) ||
                (rootNode instanceof DBNResource && node instanceof DBNResource && ((DBNResource) node).getResource() instanceof IContainer))
            {

                if (historyPosition >= 0) {
                    while (historyPosition < history.size() - 1) {
                        history.remove(historyPosition + 1);
                    }
                }
                historyPosition++;
                history.add(rootNode.getNodeItemPath());
                changeCurrentNode(node);
            } else {
                super.openNodeEditor(node);
            }
        }

        private void changeCurrentNode(DBNNode node) {
            setRootNode(node);
            loadData();
            setPartName(node.getNodeName());
            setTitleImage(DBeaverIcons.getImage(node.getNodeIcon()));
            updateActions();
        }

        @Override
        protected void fillCustomActions(IContributionManager contributionManager) {
            contributionManager.add(ActionUtils.makeCommandContribution(getSite(), IWorkbenchCommandConstants.NAVIGATE_BACKWARD_HISTORY, CommandContributionItem.STYLE_PUSH, UIIcon.RS_BACK));
            contributionManager.add(ActionUtils.makeCommandContribution(getSite(), IWorkbenchCommandConstants.NAVIGATE_FORWARD_HISTORY, CommandContributionItem.STYLE_PUSH, UIIcon.RS_FORWARD));
            contributionManager.add(new Separator());
            super.fillCustomActions(contributionManager);
        }
    }

    public static String getNodePath(DBNNode node) {
        return node.getNodeFullName();
    }
}
