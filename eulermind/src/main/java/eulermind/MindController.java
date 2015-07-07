package eulermind;

import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import eulermind.component.MindPropertyComponent;
import eulermind.view.MindView;
import eulermind.view.NodeControl;
import prefuse.data.Node;
import prefuse.data.Table;
import prefuse.data.Tree;
import prefuse.util.collections.IntIterator;

import javax.swing.*;
import javax.swing.undo.UndoManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
The MIT License (MIT)
Copyright (c) 2012-2014 wangxuguang ninesunqian@163.com

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

public class MindController extends UndoManager {
    Logger m_logger = LoggerFactory.getLogger(this.getClass());

    Hashtable<Tree, DefaultSingleCDockable> m_mindViewDockables = new Hashtable<>();

    MindModel m_mindModel;
    JLabel m_tabInfoLabel;
    CControl m_dockingCControl;

    ArrayList<NodeControl> m_externalMouseContollers = new ArrayList<NodeControl>();

    public ArrayList<Object> m_toBeLinkedDbIds = new ArrayList<>();

    public ArrayList<Tree> m_copiedSubTrees = new ArrayList<>();

    //用于记录eulermind生成的剪切板数据。
    //如果当前剪切板数据与m_clipboardTextFromHere不一致时，说明用户从其他地方复制了信息，粘贴时不能用m_copiedSubTree
    public String m_clipboardTextFormHere;

    MindController(MindModel mindModel, CControl dockingCControl, JLabel tabInfoLabel) {
        super();
        m_mindModel = mindModel;
        m_dockingCControl = dockingCControl;
        m_tabInfoLabel = tabInfoLabel;

        /*TODO 切换时更新显示lable
        m_treePanel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (m_treePanel.getSelectedComponent() != null) {
                    Component comp = m_treePanel.getSelectedComponent();
                    comp.requestFocusInWindow();
                    MindView mindView = (MindView) comp;
                    m_tabInfoLabel.setText(m_mindModel.getVertexDbIdInheritInfo(mindView.getRootDbId()));
                } else {
                    m_tabInfoLabel.setText(" ");
                }
            }
        });
        */

        ArrayList<Object> lastOpenedRootId = m_mindModel.getLastOpenedRootId();

        if (lastOpenedRootId.size() > 0) {
            for (Object rootId : lastOpenedRootId) {
                if (! m_mindModel.isVertexTrashed(rootId)) {
                    findOrAddMindView(rootId);
                }
            }
        } else {
            findOrAddMindView(m_mindModel.m_mindDb.getRootId());
        }

        //防止切换tab时，焦点被切换到工具栏
        //m_treePanel.setFocusCycleRoot(true);
    }

    public void setSwitchTabEnable(boolean enabled) {
    /*TODO: 编辑时禁止切换
        int current = m_treePanel.getSelectedIndex();
        for (int i=0; i< m_treePanel.getTabCount(); i++) {
            m_treePanel.setEnabledAt(i, enabled);

            m_treePanel.getTabComponentAt(i).setEnabled(enabled);
        }

        m_treePanel.setEnabledAt(current, true);
    */
    }

    public MindView findOrAddMindView(Object rootDBId) {

        Tree tree = m_mindModel.findOrPutTree(rootDBId);
        DefaultSingleCDockable dockable = m_mindViewDockables.get(tree);
        if (dockable != null) {
            //TODO: 切换过来： m_treePanel.setSelectedComponent(mindView);
            return (MindView)dockable.getFocusComponent();
        }

        MindView mindView = new MindView(m_mindModel, this, tree);
        dockable = new DefaultSingleCDockable(rootDBId.toString(), mindView);
        m_dockingCControl.addDockable(dockable);
        dockable.setVisible(true);

        m_mindViewDockables.put(tree, dockable);

        return  mindView;
    }

    ActionListener m_tabCloseButtonListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            /*TODO: 关闭一个树
            int pos = m_treePanel.indexOfTabComponent(buttonTabComponent);
            MindView removedMindView = (MindView) m_treePanel.getComponentAt(pos);

            if (removedMindView.isChanging()) {
                return;
            }

            for (Tree tree: m_mindViewDockables.keySet()) {
                if (m_mindViewDockables.get(tree) == removedMindView) {
                    m_mindModel.closeSubTree(tree);
                    m_mindViewDockables.remove(tree);
                    m_treePanel.remove(removedMindView);
                    return;
                }
            }
            */
        }
    };

    public MindView exposeMindView(Object rootDBId) {
        MindView mindView = findOrAddMindView(rootDBId);

        return mindView;
    }

    public void updateAllMindViews() {
        ArrayList<MindView> mindViews = new ArrayList<MindView>();
        /* 所有的树都更新
        for(int i=0; i< m_treePanel.getTabCount(); i++) {
            ((MindView) m_treePanel.getComponentAt(i)).renderTree();
        }
        */
    }

    public MindView getCurrentView() {
        /*返回当前显示的树
        return (MindView) m_treePanel.getSelectedComponent();
        */
        return null;
    }

    public boolean isChanging() {
        MindView currentView = getCurrentView();
        if (currentView == null) {
            return false;
        }
        return currentView.isChanging();
    }

    public Object getCurrentVertexId() {
        MindView currentView = getCurrentView();
        if (currentView == null) {
            return null;
        }

        Node node = currentView.getCursorSourceNode();
        return m_mindModel.getDbId(node);
    }

    String getTreeNodeRows(Tree tree)
    {
        Table nodeTable = tree.getNodeTable();
        IntIterator allRows = nodeTable.rows();

        String str = "[";

        while (allRows.hasNext()) {
             str += ((Integer)allRows.nextInt()).toString() + ", ";
        }

        str += "],  ";

        Table edgeTable = tree.getEdgeTable();
        allRows = edgeTable.rows();

        while (allRows.hasNext()) {
            str += ((Integer)allRows.nextInt()).toString() + ", ";
        }
        str += "]";

        return str;
    }

    private void removeInvalidMindViews()
    {
        HashSet<Tree> invalidTrees = new HashSet<>();

        //不能直接操作keySet， 否则会影响到内部变化
        invalidTrees.addAll(m_mindViewDockables.keySet());
        invalidTrees.removeAll(m_mindModel.getDisplaySubTrees());

        for (Tree tree : invalidTrees) {
            //TODO: test it
            m_dockingCControl.removeDockable(m_mindViewDockables.get(tree));
            m_mindViewDockables.remove(tree);
        }

        {
            invalidTrees.clear();
            invalidTrees.addAll(m_mindViewDockables.keySet());
            invalidTrees.removeAll(m_mindModel.getDisplaySubTrees());
            assert invalidTrees.size() == 0;
            assert m_mindViewDockables.size() == m_mindModel.getDisplaySubTrees().size();
        }
    }

    private void updateMindViews(MindOperator operator, boolean isUndo)
    {
        removeInvalidMindViews();

        MindView operatorBornView = exposeMindView(operator.m_rootDbId);

        for (Tree tree : m_mindViewDockables.keySet()) {

            MindView mindView = (MindView)m_mindViewDockables.get(tree).getContentPane().getComponent(0);
            mindView.setCursorAfterTreeChanged();

            if (mindView == operatorBornView) {
                mindView.setCursorNodeByPath(isUndo ? operator.m_formerCursorPath : operator.m_laterCursorPath);
            }

            mindView.renderTreeToEndChanging();
        }
    }

    private void updateMindViews()
    {
        removeInvalidMindViews();

        for (Tree tree : m_mindViewDockables.keySet()) {
            MindView mindView = (MindView)m_mindViewDockables.get(tree).getContentPane().getComponent(0);
            mindView.setCursorAfterTreeChanged();
            mindView.renderTreeToEndChanging();
        }
    }

    public void does(MindOperator operator) {
        try {
            operator.does();

            m_logger.info("m_formerCursorPath: " + operator.m_formerCursorPath.toString());
            m_logger.info("m_laterCursorPath: " + operator.m_laterCursorPath.toString());

            super.addEdit(operator);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage(), e.getMessage(), JOptionPane.ERROR_MESSAGE);
            m_logger.warn("operator exception" + e.getMessage());
        }

        updateMindViews(operator, false);
    }

    public void does(List<MindOperator> operators) {

        MindOperator lastOperator = null;

        try {
            for (MindOperator operator : operators)
            {
                if (operator.does()) {
                    //要放在前面，
                    lastOperator = operator;

                    m_logger.info("m_formerCursorPath: " + operator.m_formerCursorPath.toString());
                    m_logger.info("m_laterCursorPath: " + operator.m_laterCursorPath.toString());
                    super.addEdit(operator);

                } else {
                    /*当中间某个操作出错，立即终止。
                     * 以拖动操作为例：选集中的某个节点是拖动到上一个个节点的兄弟位置。 如果某个操作失败了，其余的就不会移动到正确位置了。
                     */
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage(), e.getMessage(), JOptionPane.ERROR_MESSAGE);
            m_logger.warn("operator exception" + e.getMessage());
        }

        if (lastOperator != null) {
            updateMindViews(lastOperator, false);
        } else {
            updateMindViews();
        }
    }

    public void redo()
    {
        if (! canRedo())
            return;

        MindOperator operator = (MindOperator)editToBeRedone();
        super.redo();

        updateMindViews(operator, false);
    }

    public void undo()
    {
        if (! canUndo())
            return;

        MindOperator operator = (MindOperator)editToBeUndone();
        super.undo();

        updateMindViews(operator, true);
    }

    HashSet<MindPropertyComponent> m_mindPropertyComponents = new HashSet<>();

    boolean m_settingNodePropertyEnabled = true;
    PropertyChangeListener m_listenerForSettingNodeProperty = new PropertyChangeListener() {

        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            String eventPropertyName = evt.getPropertyName();
            if (! eventPropertyName.startsWith(MindPropertyComponent.MIND_PROPERTY_PREFIX)) {
                return;
            }

            if (! m_settingNodePropertyEnabled) {
                return;
            }

            String propertyName = eventPropertyName.substring(MindPropertyComponent.MIND_PROPERTY_PREFIX.length());

            getCurrentView().setCursorProperty(propertyName, evt.getNewValue());
        }
    };

    public void addMindPropertyComponent(String propertyName, MindPropertyComponent mindPropertyComponent) {
        String propertyNameInComponent = MindPropertyComponent.MIND_PROPERTY_PREFIX + propertyName;
        mindPropertyComponent.setMindPropertyName(propertyNameInComponent);
        mindPropertyComponent.addPropertyChangeListener(propertyNameInComponent, m_listenerForSettingNodeProperty);
        m_mindPropertyComponents.add(mindPropertyComponent);
    }

    public void removeMindPropertyComponent(String propertyName, MindPropertyComponent mindPropertyComponent) {
        String propertyNameInComponent = MindPropertyComponent.MIND_PROPERTY_PREFIX + propertyName;
        mindPropertyComponent.setMindPropertyName(null);
        mindPropertyComponent.removePropertyChangeListener(propertyNameInComponent, m_listenerForSettingNodeProperty);
        m_mindPropertyComponents.remove(mindPropertyComponent);
    }

    public void updateMindPropertyComponents(Node node)
    {
        m_settingNodePropertyEnabled = false;
        for (String propertyName : MindModel.sm_nodePropNames) {
            Object propertyValue = node.get(propertyName);

            String propertyNameInComponent = MindPropertyComponent.MIND_PROPERTY_PREFIX + propertyName;

            for (MindPropertyComponent component : m_mindPropertyComponents) {
                if (component.getMindPropertyName().equals(propertyNameInComponent)) {
                    component.setMindPropertyValue(propertyValue);
                }
            }
        }
        m_settingNodePropertyEnabled = true;
    }

}
