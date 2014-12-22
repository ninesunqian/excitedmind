package infinitmindmap;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Demonstration of a node-link tree viewer
 * 
 * @version 1.0
 * @author <a href="http://jheer.org">jeffrey heer</a>
 */
public class TestMindDB extends TestCase {
    MindDB m_mindDB;
    String m_dbPath;

    Vertex m_root;
    Vertex m_v0;
    Vertex m_v1;

    Vertex m_v00;
    Vertex m_v01;

    Vertex m_v10;
    Vertex m_v100;

    TestMindDB(String method) {
        super(method);
    }

    public void setUp() {
        m_dbPath = System.getProperty("java.io.tmpdir") + File.separator + "mindworld_test";
        m_mindDB = new MindDB(m_dbPath);

        m_root = m_mindDB.getVertex(m_mindDB.getRootId());
        m_v0 = m_mindDB.addChild(m_root).m_vertex;
        m_v1 = m_mindDB.addChild(m_root).m_vertex;

        m_v00 = m_mindDB.addChild(m_v0).m_vertex;
        m_v01 = m_mindDB.addChild(m_v0).m_vertex;

        m_v10 = m_mindDB.addChild(m_v1).m_vertex;
        m_v100 = m_mindDB.addChild(m_v10).m_vertex;
    }

    public void tearDown() {
        try {
            FileUtils.deleteDirectory(new File(m_dbPath));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    void assertInitTree() {

        assertTrue(m_mindDB.isVertexIdSelf(m_root.getId(), m_mindDB.getParent(m_v1).getId()));
        assertTrue(m_mindDB.isVertexIdSelf(m_mindDB.getChildOrReferent(m_v1, 1).m_vertex.getId(), m_root.getId()));
        assertEquals(2, m_mindDB.getChildOrReferentCount(m_root));

        assertTrue(m_mindDB.isVertexIdParent(m_v0.getId(), m_root.getId()));
        assertTrue(m_mindDB.isVertexIdChild(m_root.getId(), m_v0.getId()));

        assertTrue(m_mindDB.isVertexIdAncestor(m_v0.getId(), m_root.getId()));
        assertTrue(m_mindDB.isVertexIdAncestor(m_v100.getId(), m_root.getId()));
        assertTrue(m_mindDB.isVertexIdDescendant(m_root.getId(), m_v100.getId()));

        assertTrue(m_mindDB.isVertexIdSibling(m_v00.getId(), m_v01.getId()));
        assertFalse(m_mindDB.isVertexIdSibling(m_v00.getId(), m_v10.getId()));

        //getSharedAncestorId [MindDB]
    }

    public void testEdge() {
        Edge refEdge = m_mindDB.addRefEdge(m_v00, m_v01);
        assertEquals(m_mindDB.getEdgeType(refEdge), MindDB.EdgeType.REFERENCE);

        //FIXME:
        assertEquals(m_mindDB.getEdgeSource(refEdge), m_v00);
        assertEquals(m_mindDB.getEdgeTarget(refEdge), m_v01);
        //getEdgeType [MindDB]

        /*
        addRefEdge [MindDB]
        removeRefEdge [MindDB]
        */

    }

    public void testInsertChild() {
        assertInitTree();
    }

    public void testRemove() {
        m_mindDB.addRefEdge(m_v100, m_root);
        //添加几个引用边
        //删除，恢复后，看是否都在
        /*
        trashSubTree [MindDB]
        restoreTrashedSubTree [MindDB]
        isVertexTrashed [MindDB]
        */
        // 删除v0, 再删除v00
        //cleanTrash [MindDB]
    }


    public void testIndex()
    {
   // getOrCreateIndex [MindDB]
        //query [MindDB]
        //createFullTextVertexKeyIndex [MindDB]
    }

    public void testHandover()
    {
        /*
        handoverChild [MindDB]
        handoverReferent [MindDB]

        changeChildOrReferentPos [MindDB]
        */
    }

    public void testProperty()
    {
        //copyProperty [MindDB]

    }
    /*
    verifyCachedInheritPathValid [MindDB]
    verifyOutEdges [MindDB]
    verifyInEdges [MindDB]
    verifyVertex [MindDB]
    verifyTrashedTree [MindDB]


     */








} // end of class TreeMap