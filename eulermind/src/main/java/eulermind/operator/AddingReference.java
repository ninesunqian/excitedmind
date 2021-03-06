package eulermind.operator;

import eulermind.MindModel;
import eulermind.MindOperator;
import prefuse.data.Edge;
import prefuse.data.Node;

import java.util.ArrayList;

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

public class AddingReference extends MindOperator {
    Object m_referrerDBId;
    Object m_referentDBId;
    int m_pos;
    ArrayList<Integer> m_referrerPath;

    ArrayList<Integer> m_referrerPathAfterDoing;

    static Logger s_logger = LoggerFactory.getLogger(AddingReference.class);

    //操作内部只需要两个dbId就够了，但是由于需要保存光标位置，所以有必要传入Node。
    //通过拖动添加的引用关系，此时的光标是被引用节点
    public AddingReference(MindModel mindModel, Node formerCursor, Node referrerNode, int pos) {
        super(mindModel, formerCursor);
        s_logger.info(String.format("formerCursor %s -- refererNode %s", m_mindModel.getNodeDebugInfo(formerCursor), m_mindModel.getNodeDebugInfo(referrerNode)));
        init(referrerNode, m_mindModel.getDbId(referrerNode), m_mindModel.getDbId(formerCursor), pos);
    }

    //操作内部只需要两个dbId就够了，但是由于需要保存光标位置，所以有必要传入Node。
    //通过输入框添加的引用关系，此时的光标是引用节点
    public AddingReference(MindModel mindModel, Node formerCursor, Object referentDBId, int pos) {
        super(mindModel, formerCursor);
        init(formerCursor, m_mindModel.getDbId(formerCursor), referentDBId, pos);
    }

    private void init(Node referrerNode, Object referrerDBId, Object referentDBId, int pos) {
        m_referrerDBId = referrerDBId;
        m_referentDBId = referentDBId;
        m_pos = pos;

        m_referrerPath = getNodePath(referrerNode);
        s_logger.info(String.format("referrerDBId %s -- referentDBId %s, referrerNodePath %s",
                referrerDBId.toString(), referentDBId.toString(), m_referrerPath.toString()));
    }

    public boolean does() {
        if (! prepareCursorInfo()) {
            return false;
        }

        Node referrer = getNodeByPath(m_referrerPath);

        m_mindModel.addReference(getNodeByPath(m_referrerPath), m_pos, m_referentDBId);

        m_referrerPathAfterDoing = getNodePath(referrer);

        m_laterCursorPath = (ArrayList<Integer>) m_referrerPathAfterDoing.clone();
        m_laterCursorPath.add(m_pos);

        return true;
    }

    public void undo() {
        Node referentNode  = getNodeByPath(m_laterCursorPath);
        Edge referEdge = referentNode.getParentEdge();
        m_mindModel.removeReference(MindModel.getDbId(referEdge));
    }

    public void redo() {
        m_mindModel.addReference(getNodeByPath(m_referrerPath), m_pos, m_referentDBId);
    }
}
