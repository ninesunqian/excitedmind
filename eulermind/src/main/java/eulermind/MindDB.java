package eulermind;

import java.nio.ByteBuffer;
import java.util.*;

import com.orientechnologies.orient.core.exception.ORecordNotFoundException;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.tinkerpop.blueprints.impls.orient.OrientVertexType;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.exception.OSerializationException;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.serialization.OSerializableStream;
import com.tinkerpop.blueprints.*;
import prefuse.util.PrefuseLib;

import com.orientechnologies.orient.core.db.record.ORecordLazyList;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;

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

public class MindDB {
    Logger m_logger = LoggerFactory.getLogger(this.getClass());

	public final static String EDGE_TYPE_PROP_NAME = "t"; //type
    public final static String EDGE_INNER_ID_PROP_NAME = "i"; //EdgeInnerId

	private final static String ROOT_INDEX_NAME = "rootIndex";
	private final static String ROOT_KEY_NAME = "root";

	private final static String TRASH_INDEX_NAME = "trashIndex";
	private final static String TRASH_KEY_NAME = "trash";

    //这几个属性使用频率不高，属性名可以长点
	private final static String SAVED_PARENT_ID_PROP_NAME = "th_parent";
	private final static String SAVED_POS_PROP_NAME = "th_pos";
	public final static String SAVED_REFERRER_INFO_PROP_NAME = "th_referrers";
    public final static String IS_TRASHED_PROP_NAME = PrefuseLib.FIELD_PREFIX + "isTrashed";

    public final static String VERTEX_CLASS = "mind_node";

    public final static int MAX_OUT_EDGES = Short.MAX_VALUE - 1;

	enum EdgeType {INCLUDE, REFERENCE};

    public static final int ADDING_EDGE_END = 0x7FFFFFFF;

	public OrientGraph m_graph;

	private Index<Vertex> m_rootIndex;
	private Index<Vertex> m_trashIndex;

    Object m_rootId;

	String m_path;

    final int PARENT_DB_ID_CACHE_CAPACITY = 2048;
    final int OUT_EDGE_INNER_ID_CACHE_CAPACITY = 2048;

    LinkedHashMap<Object, Object> m_parentDbIdCache =
            new LinkedHashMap<Object, Object>(256, 0.75f, true) {

          protected boolean removeEldestEntry (Map.Entry<Object, Object> eldest) {
             return size() > PARENT_DB_ID_CACHE_CAPACITY;
          }
     };

    LinkedHashMap<Object, List<OutEdgeIdPair>> m_outEdgeInnerIdCache =
            new LinkedHashMap<Object, List<OutEdgeIdPair>>(256, 0.75f, true) {

                protected boolean removeEldestEntry (Map.Entry<Object, List<OutEdgeIdPair>> eldest) {
                    return size() > OUT_EDGE_INNER_ID_CACHE_CAPACITY;
                }
            };

    MindDB(String path)
	{
		m_graph = new OrientGraph (path, false);
		m_path = path;

        m_rootIndex = getOrCreateIndex(ROOT_INDEX_NAME);
        m_trashIndex = getOrCreateIndex(TRASH_INDEX_NAME);

        createFullTextVertexKeyIndex(MindModel.TEXT_PROP_NAME);

        Vertex root = null;
        if (m_rootIndex.get(ROOT_KEY_NAME, ROOT_KEY_NAME).iterator().hasNext()) {
            root = m_rootIndex.get(ROOT_KEY_NAME, ROOT_KEY_NAME).iterator().next();

        } else {
            root = addVertex(null);
            m_rootIndex.put(ROOT_KEY_NAME, ROOT_KEY_NAME, root);

            //translate the root id from temporary to permanence
            m_graph.commit();
        }

        m_rootId = root.getId();
	}

    int m_vertexCount = 0;
	private Vertex addVertex(Object arg0) {
        Vertex vertex =  m_graph.addVertex(null, MindModel.TEXT_PROP_NAME, "a");
        m_vertexCount++;
        return m_graph.getVertex(vertex.getId());
	}

    public int getVertexCount() {
        return m_vertexCount;
    }

	public Edge getEdge(Object arg0) {
		return m_graph.getEdge(arg0);
	}

	public Vertex getVertex(Object arg0) {
		return m_graph.getVertex(arg0);
	}

	public void shutdown() {
		cleanTrash ();
		m_graph.shutdown();
	}
	
	public void commit() {
		m_graph.commit();
	}

    public Index<Vertex> getOrCreateIndex(String indexName)
	{
        Index<Vertex> index = m_graph.getIndex(indexName, Vertex.class);
        if (index == null) {
            index = m_graph.createIndex(indexName, Vertex.class);
        }

        return index;
	}

    public void dropIndex(String indexName)
    {
        //FIXME: dropIndex后马上 createIndex, 会有bug，提示该index已经存在
        m_graph.dropIndex(indexName);
    }

	public Object getRootId ()
	{
		return m_rootId;
	}
	
	public Vertex getEdgeSource (Edge edge)
	{
		return edge.getVertex(Direction.OUT);
	}
	
	public Vertex getEdgeTarget (Edge edge)
	{
		return edge.getVertex(Direction.IN);
	}
	
	public <T> ArrayList<T> getContainerProperty (Element source, String propName)
	{
		//Because outEdgeArray must be convert to ORecordLazyList, so its type is not ArrayList.
		Object container = source.getProperty(propName);
		if (container == null)
		{
            return new ArrayList<T> ();
		}
		
		if (container instanceof ORecordLazyList)
		{
			ORecordLazyList implArray = (ORecordLazyList)container;
			implArray.setAutoConvertToRecord(false);
		}

        //return a copy list, to avoid being clear by Graph.commit
        return new ArrayList((ArrayList<Object>)container);
	}

    public void setContainerProperty(Vertex source, String propName, ArrayList container)
    {
        if (container.isEmpty()) {
            source.removeProperty(propName);
        } else {
            source.setProperty(propName, container);
        }
    }

    public static String getOutEdgeInnerId(Edge edge)
    {
        return edge.getProperty(EDGE_INNER_ID_PROP_NAME);
    }

    public OutEdgeIdPair getOutEdgeIdPair(Edge edge) {
        return new OutEdgeIdPair(this, edge.getId(), getOutEdgeInnerId(edge));
    }

    private List<OutEdgeIdPair> getOutEdgeIdPairsSkipCache(Vertex source)
    {
        ArrayList<OutEdgeIdPair> outEdgeIdPair = new ArrayList<>();
        ArrayList<Edge> noInnerIdEdges = new ArrayList<>();

        Iterator<Edge> outEdgeIterator = source.getEdges(Direction.OUT).iterator();
        while (outEdgeIterator.hasNext())
        {
            Edge outEdge = outEdgeIterator.next();
            String innerId = getOutEdgeInnerId(outEdge);
            if (innerId != null) {
                outEdgeIdPair.add(new OutEdgeIdPair(this, outEdge.getId(), innerId));
            } else {
                noInnerIdEdges.add(outEdge);
            }
        }

        Collections.sort(outEdgeIdPair);

        for (Edge noInnerIdEdge : noInnerIdEdges) {
            allocateOutEdgeInnerId(outEdgeIdPair, ADDING_EDGE_END, noInnerIdEdge);
        }

        return outEdgeIdPair;
    }

	private List<OutEdgeIdPair> getOutEdgeIdPairsNoCopy(Vertex source)
	{
        //TODO mindModel用来判断Node是不是更新过了，应该不需要
        List<OutEdgeIdPair> outEdgeIdPairs = m_outEdgeInnerIdCache.get(source.getId());
        if (outEdgeIdPairs == null) {
            outEdgeIdPairs = getOutEdgeIdPairsSkipCache(source);
            m_outEdgeInnerIdCache.put(source.getId(), outEdgeIdPairs);
        }
        return outEdgeIdPairs;
	}

    public List<OutEdgeIdPair> getOutEdgeIdPairs(Vertex source)
    {
        ArrayList<OutEdgeIdPair> outEdgeIdPairs = (ArrayList<OutEdgeIdPair>)getOutEdgeIdPairsNoCopy(source);
        //return (ArrayList<OutEdgeIdPair>)outEdgeIdPairs.clone();
        return Collections.unmodifiableList(outEdgeIdPairs);
    }

    private EdgeVertex getParentSkipCache(Vertex vertex)
    {
        if (vertex.getId().toString().equals("#9:2")) {
            int debug = 1;
        }
        Iterator<Edge> edgeIterator = vertex.getEdges(Direction.IN).iterator();
        Edge parentToVertex = null;

        while (edgeIterator.hasNext())
        {
            Edge edge = edgeIterator.next();

            if (getEdgeType(edge) == EdgeType.INCLUDE) {
                parentToVertex = edge;
                break;
            }
        }

        if (parentToVertex == null) {
            return null;

        } else {
            Vertex parent = getEdgeSource(parentToVertex);
            return new EdgeVertex(parent, vertex, parentToVertex, -1);
        }
    }

    public List getInheritPath(Object dbId)
    {
        assert !(dbId instanceof Vertex);

        LinkedList inheritPath = new LinkedList();

        Object parentDbId = getParentDbId(dbId);
        while (parentDbId != null) {
            inheritPath.addFirst(parentDbId);
            parentDbId = getParentDbId(parentDbId);
        }

        return inheritPath;
    }

    Object getSharedAncestorId(Object vertexId1, Object vertexId2) {

        List inheritPath1 = getInheritPath(vertexId1);
        List inheritPath2 = getInheritPath(vertexId2);

        //FIXME; 如果有一个被删除了呢
        Object sharedAncestorId = null;

        for (int i = 0; i < inheritPath1.size() && i < inheritPath2.size(); i++)
        {
            if (! inheritPath1.get(i).equals(inheritPath2.get(i))) {
                if (i > 0) {
                    sharedAncestorId = inheritPath1.get(i - 1);
                }
                break;
            }
        }

        assert sharedAncestorId != null;

        return sharedAncestorId;
    }

    boolean vertexIdIsSelf(Object thiz, Object that) {
        return thiz.equals(that);
    }

    boolean vertexIdIsParentOf(Object thiz, Object that) {
        return vertexIdIsSelf(thiz, getParentDbId(that));
    }

    boolean vertexIdIsChildOf(Object thiz, Object that) {
        return vertexIdIsSelf(getParentDbId(thiz), that);
    }

    boolean vertexIdIsSiblingOf(Object thiz, Object that) {
        return vertexIdIsSelf(getParentDbId(thiz), getParentDbId(that));
    }


    boolean vertexIdIsDescendantOf(Object thiz, Object that) {
        List thizInheritPath = getInheritPath(thiz);
        return thizInheritPath.contains(that);
    }

    boolean vertexIdIsAncestorOf(Object thiz, Object that) {
        List thatInheritPath = getInheritPath(that);
        return thatInheritPath.contains(thiz);
    }

    boolean subTreeContainsVertexId(Object subTreeId, Object vertexId) {
        return vertexIdIsAncestorOf(subTreeId, vertexId) || vertexIdIsSelf(subTreeId, vertexId);
    }

	public EdgeType getEdgeType(Edge edge)
	{
        int edgeTypeValue = edge.getProperty(EDGE_TYPE_PROP_NAME);
		return EdgeType.values()[edgeTypeValue];
	}

    private char[] stringIdToCharArray(String id, int arraySize, char padding) {
        assert id.length() < arraySize;

        char chars[] = new char[arraySize];

        int i, j;

        for (i=0; i<id.length(); i++) {
            chars[i] = id.charAt(i);
        }

        for (j=i; j<arraySize; j++) {
            chars[j] = padding;
        }

        return chars;
    }

    private String getMiddleString(String lower, String upper)
    {
        //字符串比较类似纯小数的比较 "ab" < "ac" 类似  0.01 < 0.03
        //  a看作0，  z看作9

        assert lower != null;
        assert upper != null;

        if (lower.length() == 0 && upper.length() == 0) {
            return  "h"; // "h" = 0.2, 根据二八定律，用户很有可能在后面追加节点
        }

        assert lower.compareTo(upper) < 0 || upper.length() == 0;

        int charCount = lower.length() > upper.length() ? lower.length() : upper.length();
        charCount += 2; //补上两位用户扩展

        char lowerChars[] = stringIdToCharArray(lower, charCount, 'a');
        char upperChars[] =  upper.length() == 0 ?
                stringIdToCharArray("", charCount, 'z') :  stringIdToCharArray(upper, charCount, 'a');

        int i = 0;

        while(lowerChars[i] == upperChars[i]) {
            i++;
        }

        if (lowerChars[i] + 1 == upperChars[i]) {
            //0.19994 , 0.2 中间有一个 0.19995

            i++;

            //跳过中间的999
            while (lowerChars[i] == 'z') {
                i++;
            }

            if (lowerChars[i] == 'a') {
                //如果是 0.19990, 返回 0.19992
                lowerChars[i] = 'h';
            } else {
                //如果是 0.19993, 返回 0.19994
                lowerChars[i]++;
            }

        } else {
            //0.19999,  0.4123 之间有一个0.2
            lowerChars[i]++;
        }

        return new String(lowerChars, 0, i+1);
    }


    private String allocateOutEdgeInnerId(List<OutEdgeIdPair> outEdgeIdPairs, int pos, Edge edge)
    {
        String newInnerId;
        String upper;
        String lower;

        if (pos == ADDING_EDGE_END) {
            pos = outEdgeIdPairs.size();
        }

        assert pos <= outEdgeIdPairs.size();

        if (pos == 0) {
            lower = "";
        } else  {
            lower = outEdgeIdPairs.get(pos - 1).m_innerId;
        }

        if (pos == outEdgeIdPairs.size()) {
            upper = "";
        } else {
            upper = outEdgeIdPairs.get(pos).m_innerId;
        }

        newInnerId = getMiddleString(lower, upper);

        edge.setProperty(EDGE_INNER_ID_PROP_NAME, newInnerId);
        outEdgeIdPairs.add(pos, new OutEdgeIdPair(this, edge.getId(), newInnerId));

        return newInnerId;
    }

	private Edge addEdge(Vertex source, Vertex target, int pos, EdgeType edgeType)
	{
        //必须先取出outEdgeIds。否则，由于cache中没有而从数据库中刷新，新加的边就在其中了
        List<OutEdgeIdPair> outEdgeIds = getOutEdgeIdPairsNoCopy(source);

		Edge edge = m_graph.addEdge(null, source, target, "E");

        String outEdgeInnerId = allocateOutEdgeInnerId(outEdgeIds, pos, edge);

		edge.setProperty(EDGE_TYPE_PROP_NAME, edgeType.ordinal());
        edge.setProperty(EDGE_INNER_ID_PROP_NAME, outEdgeInnerId);
		
		return edge;
	}

    public Edge addRefEdge(Vertex referrer, Vertex referent)
    {
        return addRefEdge(referrer, referent, ADDING_EDGE_END);
    }

    public Edge addRefEdge(Vertex referrer, Vertex referent, int pos)
    {
        Edge edge = addEdge(referrer, referent, pos, EdgeType.REFERENCE);
        verifyVertex(referrer);
        verifyVertex(referent);

        return edge;
    }

    private void removeOutEdgeIdPairFromCache(Vertex source, int pos)
    {
        List cachedInnerId = m_outEdgeInnerIdCache.get(source.getId());
        if (cachedInnerId != null) {
            cachedInnerId.remove(pos);
        }
    }

    private void removeEdge (Vertex source, int pos, EdgeType assert_type)
    {
        //取边也会过cache
        Edge edgeBeingRemoved = getEdge(source, pos);
        assert edgeBeingRemoved != null;
        assert getEdgeType(edgeBeingRemoved) == assert_type;

        //需要先更新cache
        removeOutEdgeIdPairFromCache(source, pos);

        m_graph.removeEdge(edgeBeingRemoved);
    }

    //不用pos作为参数。应为删除一个边后，其他边的pos会改变，增加复杂性
    public void removeRefEdge(Vertex source, Edge edge)
    {
        assert getEdgeType(edge) == EdgeType.REFERENCE;
        assert vertexIdIsSelf(source.getId(), edge.getVertex(Direction.OUT).getId());

        Vertex referrent = edge.getVertex(Direction.IN);
        m_graph.removeEdge(edge);

        List<OutEdgeIdPair> cachedInnerId = m_outEdgeInnerIdCache.get(source.getId());
        if (cachedInnerId != null) {
            for (OutEdgeIdPair outEdgeIdPair : cachedInnerId) {
                if (outEdgeIdPair.m_dbId.equals(edge.getId())) {
                    cachedInnerId.remove(outEdgeIdPair);
                    break;
                }
            }
        }

        verifyVertex(source);
        verifyVertex(referrent);
    }

    public Edge getEdge(Vertex source, int pos)
	{
		List<OutEdgeIdPair> outEdgeIds = getOutEdgeIdPairsNoCopy(source);

        if (outEdgeIds == null) {
            assert source.getEdges(Direction.OUT).iterator().hasNext() == false;
            return null;
        }

        return getEdge(outEdgeIds.get(pos).m_dbId);
	}

    //这是一个保存临时信息的类
	static public class EdgeVertex implements Comparable <EdgeVertex> {
		final public Vertex m_source;
        final public Vertex m_target;
        final public Edge m_edge;
        final public String m_edgeInnerId;

        final public int m_edgePos; //edgePos 不一定用到，-1表示未知，需要用户自己计算

		public EdgeVertex(Vertex source, Vertex target, Edge edge, int edgePos) {
			m_source = source;
			m_edge = edge;
            m_target = target;
            m_edgePos = edgePos;
            m_edgeInnerId = getOutEdgeInnerId(edge);

            assert  m_edge.getVertex(Direction.OUT).getId().equals(m_source);
            assert  m_edge.getVertex(Direction.IN).getId().equals(m_target);
		}

        public int compareTo(EdgeVertex other) {
            if (! m_source.getId().equals(m_target.getId())) {
                return m_source.getId().hashCode() - m_target.getId().hashCode();
            } else {
                return getOutEdgeInnerId(m_edge).compareTo(getOutEdgeInnerId(other.m_edge));
            }
        }
	};

    public EdgeVertex addChild (Vertex parent)
    {
        return addChild(parent, ADDING_EDGE_END);
    }

	public EdgeVertex addChild (Vertex parent, int pos)
	{
        m_logger.debug("MindDB insert at {}", pos);

		Vertex child = addVertex(null);
        ORecordId childId = (ORecordId) child.getId();

        //文档上说新建的节点id都是临时，经过测试id不是临时的，所以加入了判断
        if (childId.isTemporary()) {
            commit();
            child = getVertex(child.getId());
            parent = getVertex(parent.getId());
        }

        Edge edge = addEdge(parent, child, pos, EdgeType.INCLUDE);
        m_parentDbIdCache.put(child.getId(), parent.getId());

        verifyVertex(parent);
        verifyVertex(child);

        return new EdgeVertex(parent, child, edge, pos);
	}

    public int getChildOrReferentCount(Vertex vertex)
    {
        return getOutEdgeIdPairsNoCopy(vertex).size();
    }

	public EdgeVertex getChildOrReferent(Vertex parent, int pos)
	{
		Edge edge = getEdge(parent, pos);
		if (edge == null)
		{
			return null;
		}
		else
		{
			Vertex child = getEdgeTarget(edge);
			return new EdgeVertex(parent, child, edge, pos);
		}
	}
	
	public ArrayList<EdgeVertex> getChildrenAndReferents(Vertex parent)
	{
		List<OutEdgeIdPair> outEdgeIdPairs = getOutEdgeIdPairsNoCopy(parent);
		if (outEdgeIdPairs == null)
			return null;
		
		ArrayList<EdgeVertex> children = new ArrayList<EdgeVertex>();

		children.ensureCapacity(outEdgeIdPairs.size());

		for (int i=0; i<outEdgeIdPairs.size(); i++)
		{
			Edge edgeToChild = getEdge(outEdgeIdPairs.get(i).m_dbId);
			Vertex child = getEdgeTarget(edgeToChild);
			children.add(new EdgeVertex(parent, child, edgeToChild, i));
		}
		return children;
	}
	
    public Object getParentDbId(Object dbId)
    {
        if (dbId.equals(m_rootId) || isInTrashIndex(dbId)) {
            return null;
        }

        Object cachedParentDbId = m_parentDbIdCache.get(dbId);

        if (cachedParentDbId != null) {
            return cachedParentDbId;
        } else {
            EdgeVertex toParent = getParentSkipCache(getVertex(dbId));
            if (toParent == null || toParent.m_source == null) {
                int i= 1;
            }
            Object parentDbId = toParent.m_source.getId();
            assert(!(dbId instanceof Vertex));
            assert(!(parentDbId instanceof Vertex));
            m_parentDbIdCache.put(dbId, parentDbId);
            return parentDbId;
        }
    }

    public Vertex getParent(Vertex vertex)
    {
        if (vertex.getId().equals(m_rootId)) {
            return null;
        }

        Object cachedParentDbId = m_parentDbIdCache.get(vertex.getId());
        if (cachedParentDbId == null) {
            EdgeVertex toParent = getParentSkipCache(vertex);
            m_parentDbIdCache.put(vertex.getId(), toParent.m_source.getId());
            return toParent.m_source;
        } else {
            return getVertex(cachedParentDbId);
        }
    }

    public EdgeVertex handoverChild(Vertex fromParent, int fromPos, Vertex toParent, int toPos)
    {
        Vertex child = getChildOrReferent(fromParent, fromPos).m_target;
        removeEdge (fromParent, fromPos, EdgeType.INCLUDE);
        Edge edge = addEdge(toParent, child, toPos, EdgeType.INCLUDE);

        //新的父节点，不能是子节点的后代
        assert ! vertexIdIsAncestorOf(child.getId(), toParent.getId());

        m_parentDbIdCache.put(child.getId(), toParent.getId());

        verifyVertex(fromParent);
        verifyVertex(toParent);
        verifyVertex(child);

        return new EdgeVertex(toParent, child, edge, toPos);
    }

    public EdgeVertex handoverReferent(Vertex fromReferrer, int fromPos, Vertex toReferrer, int toPos)
    {
        Vertex referent = getChildOrReferent(fromReferrer, fromPos).m_target;
        removeEdge (fromReferrer, fromPos, EdgeType.REFERENCE);
        Edge edge = addEdge(toReferrer, referent, toPos, EdgeType.REFERENCE);

        verifyVertex(fromReferrer);
        verifyVertex(toReferrer);
        verifyVertex(referent);

        return new EdgeVertex(toReferrer, referent, edge, toPos);
    }

    public void changeChildOrReferentPos(Vertex parent, int oldPos, int newPos)
    {
        if (oldPos == newPos)
            return;

        List<OutEdgeIdPair> outEdgeIds = getOutEdgeIdPairsNoCopy(parent);
        Object edgeDbId = outEdgeIds.get(oldPos).m_dbId;

        outEdgeIds.remove(oldPos);

        allocateOutEdgeInnerId(outEdgeIds, newPos, getEdge(edgeDbId));

        parent = m_graph.getVertex(parent.getId());
        verifyVertex(parent);
    }

    public ArrayList<EdgeVertex> getReferrers(Vertex referent)
	{
		Iterator<Edge> edgeIterator = referent.getEdges(Direction.IN).iterator();
		Edge refEdge;
		ArrayList<EdgeVertex> referrerArray = new ArrayList<> ();
		
		while (edgeIterator.hasNext())
		{
			refEdge = edgeIterator.next();
			
			if (getEdgeType(refEdge) == EdgeType.REFERENCE)
			{
				Vertex referrer = getEdgeSource(refEdge);
				referrerArray.add(new EdgeVertex(referrer, referent, refEdge, -1));
			}
		}

        Collections.sort(referrerArray);
		
		return referrerArray.size() == 0 ? null : referrerArray;
	}
	
	private interface Processor 
	{
	 	abstract public boolean run (Vertex vertex, int level);
	}
	
	private void deepTraverse (Vertex vertex, Processor proc, int level)
	{
		if (proc.run(vertex, level))
		{
			ArrayList<EdgeVertex> children = getChildrenAndReferents(vertex);
			
			if (children != null)
			{

				for (EdgeVertex child : children)
				{
					if (getEdgeType(child.m_edge) == EdgeType.INCLUDE)
					{
						deepTraverse(child.m_target, proc, level+1);
					}
				}
			}
		}
	}

	private void deepTraverse (Vertex vertex, Processor proc)
	{
		deepTraverse(vertex, proc, 0);
	}

    //删除子树时用，保存子树之外的节点到子树之内的节点的引用关系
	public static class RefLinkInfo implements OSerializableStream {
		public final Object m_referrer;
		public final Object m_referent;
        public final Object m_edge;
		public final int m_pos;
		
		RefLinkInfo (Object referrer, Object referent, Object edge, int pos)
		{
			m_referrer = referrer;
			m_referent = referent;
            m_edge = edge;
			m_pos = pos;

            assert referent instanceof ORecordId;
            assert referrer instanceof ORecordId;
            assert edge instanceof ORecordId;
		}

        public byte[] toStream() throws OSerializationException
        {
            byte referrerBytes[] = ((ORecordId)m_referrer).toStream();
            byte referentBytes[] = ((ORecordId)m_referent).toStream();
            byte edgeBytes[] = ((ORecordId)m_edge).toStream();

            ByteBuffer byteBuffer = ByteBuffer.allocate(16 + referrerBytes.length + referentBytes.length + edgeBytes.length);

            byteBuffer.putInt(m_pos);
            byteBuffer.putInt(referrerBytes.length);
            byteBuffer.putInt(referentBytes.length);
            byteBuffer.putInt(edgeBytes.length);

            byteBuffer.put(referrerBytes);
            byteBuffer.put(referentBytes);
            byteBuffer.put(edgeBytes);

            //orientdb内部不是直接存byte[], 而是先把它当成utf-8字符串先解析一遍，再存储。不知道为什么这样
            //所以这里用base64 转换一下
            return Base64.encodeBase64(byteBuffer.array());
        }

        public RefLinkInfo fromStream(byte[] iStream) throws OSerializationException
        {
            iStream = Base64.decodeBase64(iStream);
            ByteBuffer byteBuffer = ByteBuffer.wrap(iStream);
            int pos = byteBuffer.getInt();
            int referrerByteLength = byteBuffer.getInt();
            int referentByteLength = byteBuffer.getInt();
            int edgeByteLength = byteBuffer.getInt();

            int from = 16;
            int to = from + referrerByteLength;
            byte referrerByte[] = Arrays.copyOfRange(iStream, from, to);

            from = to;
            to += referentByteLength;
            byte referentByte[] = Arrays.copyOfRange(iStream, from, to);

            from = to;
            to += edgeByteLength;
            byte edgeByte[] = Arrays.copyOfRange(iStream, from, to);

            assert to == iStream.length;

            ORecordId referrer = (new ORecordId()).fromStream(referrerByte);
            ORecordId referent = (new ORecordId()).fromStream(referentByte);
            ORecordId edge = (new ORecordId()).fromStream(edgeByte);
            return new RefLinkInfo(referrer, referent, edge, pos);
        }
    }
	
	//return the removed vertex
	public Vertex trashSubTree(Vertex parent, int pos)
	{
		EdgeVertex edgeVertex = getChildOrReferent(parent, pos);
		
		assert (getEdgeType(edgeVertex.m_edge) == EdgeType.INCLUDE);
		
		final Vertex removedVertex = edgeVertex.m_target;

        //collect the refer info, to help update display tree
        final ArrayList<RefLinkInfo> refLinkInfos = new ArrayList<> ();

		deepTraverse(removedVertex, new Processor() {
			
			public boolean run(Vertex vertex, int level) {
				ArrayList<EdgeVertex> referrers = getReferrers(vertex);
				
				if (referrers != null)
				{
					for (EdgeVertex referrer : referrers)
					{
                        List<OutEdgeIdPair> outEdgeIdPairsOfReferrer = m_outEdgeInnerIdCache.get(referrer.m_source.getId());
                        if (outEdgeIdPairsOfReferrer == null) {
                            outEdgeIdPairsOfReferrer = getOutEdgeIdPairsSkipCache(referrer.m_source);
                        }

                        //此处不必再加入m_outEdgeInnerIdCache

						int edgeIndex = outEdgeIdPairsOfReferrer.indexOf(getOutEdgeIdPair(referrer.m_edge));
                        Object referrerId = referrer.m_source.getId();

                        //仅仅删除子树之外的节点到子树之内的节点的引用关系
                        if (! subTreeContainsVertexId(removedVertex.getId(), referrerId)) {
                            refLinkInfos.add(new RefLinkInfo(referrerId, vertex.getId(), referrer.m_edge.getId(), edgeIndex));
                            removeRefEdge(referrer.m_source, referrer.m_edge);
                        }

					}
				}

                vertex.setProperty(IS_TRASHED_PROP_NAME, true);

				return true;
			}
		});
		
		removedVertex.setProperty(SAVED_PARENT_ID_PROP_NAME, parent.getId());
		removedVertex.setProperty(SAVED_POS_PROP_NAME, pos);
        setContainerProperty(removedVertex, SAVED_REFERRER_INFO_PROP_NAME, refLinkInfos);

        removeEdge(parent, pos, EdgeType.INCLUDE);

        //不必从m_outEdgeInnerIdCache删除removedVertex的出边信息。因为用户有可能后退。

		m_trashIndex.put(TRASH_KEY_NAME, TRASH_KEY_NAME, removedVertex);

        verifyTrashedTree(removedVertex);

		return removedVertex;
	}

    public static class TrashedTreeContext {
        Object m_parentId;
        int m_pos;
        ArrayList<RefLinkInfo> m_refLinkInfos;

        TrashedTreeContext (Object parentId, int pos, ArrayList<RefLinkInfo> refLinkInfos)
        {
            m_parentId = parentId;
            m_pos = pos;
            m_refLinkInfos = refLinkInfos;
        }
    }

    public TrashedTreeContext getTrashedTreeContext (Vertex vertex)
    {
        Object parentId = vertex.getProperty(SAVED_PARENT_ID_PROP_NAME);
        if (parentId instanceof Vertex) {
            parentId = ((Vertex)parentId).getId();
        }

        int pos = (Integer)vertex.getProperty(SAVED_POS_PROP_NAME);
        ArrayList<Object> refLinkInfoes = getContainerProperty(vertex, SAVED_REFERRER_INFO_PROP_NAME);

        assert (parentId != null);

        if (refLinkInfoes == null) {
            return new TrashedTreeContext (parentId, pos, null);

        } else {
            ArrayList<RefLinkInfo> new_array = new ArrayList<RefLinkInfo>();
            for (Object obj : refLinkInfoes)
            {
                RefLinkInfo refLinkInfo = (RefLinkInfo) obj;
                new_array.add(refLinkInfo);
            }

            return new TrashedTreeContext (parentId, pos, new_array);
        }

    }
	
	//return parent vertex, and 
	public EdgeVertex restoreTrashedSubTree(Vertex vertex)
	{
		Object parentId = vertex.getProperty(SAVED_PARENT_ID_PROP_NAME);
        if (parentId instanceof Vertex) {
            parentId = ((Vertex)parentId).getId();
        }

		int pos = (Integer)vertex.getProperty(SAVED_POS_PROP_NAME);
		ArrayList<Object> refLinkInfoes = getContainerProperty(vertex, SAVED_REFERRER_INFO_PROP_NAME);
		
		Vertex parent = getVertex(parentId);
		Edge edge = addEdge(parent, vertex, pos, EdgeType.INCLUDE);
		
		if (refLinkInfoes != null)
		{
			for (Object obj : refLinkInfoes)
			{
				RefLinkInfo refLinkInfo = (RefLinkInfo) obj;
				addEdge(getVertex(refLinkInfo.m_referrer),
                        getVertex(refLinkInfo.m_referent),
                        refLinkInfo.m_pos,
                        EdgeType.REFERENCE);
                verifyVertex(getVertex(refLinkInfo.m_referrer));
                verifyVertex(getVertex(refLinkInfo.m_referent));
			}
		}

        deepTraverse(vertex, new Processor() {
            public boolean run(Vertex vertex, int level) {
                vertex.removeProperty(IS_TRASHED_PROP_NAME);
                return true;
            }
        });

		vertex.removeProperty(SAVED_PARENT_ID_PROP_NAME);
		vertex.removeProperty(SAVED_POS_PROP_NAME);
		vertex.removeProperty(SAVED_REFERRER_INFO_PROP_NAME);

        m_trashIndex.remove(TRASH_KEY_NAME, TRASH_KEY_NAME, vertex);

        assert(!(parentId instanceof Vertex));
        m_parentDbIdCache.put(vertex.getId(), parentId);
		return new EdgeVertex(parent, vertex, edge, pos);
	}

    private void removeSubTree(Vertex root)
    {
        for (EdgeVertex edgeVertex : getChildrenAndReferents(root)) {
            if (getEdgeType(edgeVertex.m_edge) == EdgeType.INCLUDE) {
                removeSubTree(edgeVertex.m_target);
            }
        }

        try {
            m_graph.removeVertex(root);

        } catch (ORecordNotFoundException e) {
            m_logger.warn("remove Vertex: not found: {}, exception {}", root.getId(), e);
        }
    }

    private boolean isInTrashIndex(Object dbId) {
        for (Vertex trashedRoot : m_trashIndex.get(TRASH_KEY_NAME, TRASH_KEY_NAME)) {
            if (dbId.equals(trashedRoot.getId())) {
                return true;
            }
        }
        return false;
    }

	private void cleanTrash ()
	{
        for (Vertex vertex: m_trashIndex.get(TRASH_KEY_NAME, TRASH_KEY_NAME)) {
            try {
                removeSubTree(vertex);
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                m_logger.error("RemoveSubTree: {}: {}", vertex.getId(), vertex.getProperty(MindModel.TEXT_PROP_NAME));
            }
        }
	}
	
	public void copyProperty(Element from, Element to)
	{
        assert from.getClass() == to.getClass();
		for (String key : from.getPropertyKeys())
		{
            to.setProperty(key, from.getProperty(key));
		}
	}

	public GraphQuery query() {
		return m_graph.query();
	}

    public void createFullTextVertexKeyIndex(String key)
    {
        Set<String> indexedKeys = m_graph.getIndexedKeys(Vertex.class);

        for (String indexedKey : indexedKeys) {
            if (indexedKey.equals(key)) {
                return;
            }
        }

        OrientVertexType type = m_graph.getVertexBaseType();
        type.createProperty(key, OType.STRING);

        type.createIndex("V."+key, "FULLTEXT", null, null, "LUCENE", new String[]{key});
    }

    public Iterable<Vertex> getVertices(String key, String value)
    {
        return m_graph.getVertices(key, value);
    }

    public Iterable<Vertex> getVertices(final String label, final String[] iKey, Object[] iValue) {
        return m_graph.getVertices(label, iKey, iValue);
    }

    private void verifyCachedInheritPathValid(Object parentDbId, Object childDbId)
    {
        List childInheritPath = getInheritPath(childDbId);
        List parentInheritPath = getInheritPath(parentDbId);
        assert childInheritPath.get(childInheritPath.size()-1).equals(parentDbId) &&
                childInheritPath.subList(0, childInheritPath.size()-1).equals(parentInheritPath);
    }

    private void verifyOutEdges(Vertex vertex)
    {
        List<OutEdgeIdPair> outEdgeIdPairs = getOutEdgeIdPairsNoCopy(vertex);
        Map<String, Integer> outEdgeIdPairExists = new HashMap<>();

        for(OutEdgeIdPair outEdgeIdPair : outEdgeIdPairs) {
            outEdgeIdPairExists.put(outEdgeIdPair.m_innerId, 0);
        }

        int edgeNumber = 0;
        for (Edge outEdge : vertex.getEdges(Direction.OUT)) {
            edgeNumber++;

            OutEdgeIdPair outEdgeIdPair = getOutEdgeIdPair(outEdge);
            assert outEdgeIdPairs.contains(outEdgeIdPair);
            outEdgeIdPairExists.put(outEdgeIdPair.m_innerId, outEdgeIdPairExists.get(outEdgeIdPair.m_innerId) + 1);

            if (getEdgeType(outEdge) == EdgeType.INCLUDE) {
                Vertex childVertex = getEdgeTarget(outEdge);
                verifyCachedInheritPathValid(vertex.getId(), childVertex.getId());

            } else {
                assert getEdgeType(outEdge) == EdgeType.REFERENCE;
            }
        }

        Iterator iter = outEdgeIdPairExists.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<OutEdgeIdPair, Integer> entry = (Map.Entry) iter.next();
            assert entry.getValue() == 1;
        }

        assert (edgeNumber == outEdgeIdPairs.size());
    }

    private void verifyInEdges(Vertex vertex)
    {
        int metParent = 0;

        for (Edge inEdge : vertex.getEdges(Direction.IN)) {
            Vertex parentOrReferrerVertex = getEdgeSource(inEdge);

            List<OutEdgeIdPair> parentOrReferrerOutEdgeIdPairs = getOutEdgeIdPairsNoCopy(parentOrReferrerVertex);
            assert parentOrReferrerOutEdgeIdPairs.contains(getOutEdgeIdPair(inEdge));

            if (getEdgeType(inEdge) == EdgeType.INCLUDE) {
                verifyCachedInheritPathValid(parentOrReferrerVertex.getId(), vertex.getId());
                metParent ++;
            } else {
                assert(getEdgeType(inEdge) == EdgeType.REFERENCE);
            }
        }

        if (!vertex.getId().equals(m_rootId)) {
            assert metParent == 1;
        } else {
            assert metParent == 0;
        }
    }

    public void verifyVertex(Vertex vertex)
    {
        //after commit, must add it
        vertex = m_graph.getVertex(vertex.getId());

        verifyInEdges(vertex);
        verifyOutEdges(vertex);
    }

    public boolean isVertexTrashed(Vertex vertex)
    {
        return vertex.getProperty(IS_TRASHED_PROP_NAME) != null;
    }

    private void setVertexTrashed(Vertex vertex, boolean trashed)
    {
        if (trashed == true)
            vertex.setProperty(IS_TRASHED_PROP_NAME, true);
        else
            vertex.removeProperty(IS_TRASHED_PROP_NAME);
    }

    public void verifyTrashedTree(final Vertex root)
    {
        assert isInTrashIndex(root.getId());

        Iterable<Edge> inEdges = root.getEdges(Direction.IN);
        assert inEdges.iterator().hasNext() == false;

        Object parentId = root.getProperty(SAVED_PARENT_ID_PROP_NAME);

        //FIXME: in orientdb, if a property is a vertexId, getProperty translate it to Vertex
        if (parentId instanceof Vertex){
            parentId = ((Vertex) parentId).getId();
        }
        Integer pos = root.getProperty(SAVED_POS_PROP_NAME);
        ArrayList<RefLinkInfo> refLinkInfos = (ArrayList<RefLinkInfo>)root.getProperty(SAVED_REFERRER_INFO_PROP_NAME);

        assert parentId != null;
        assert pos != null;
        assert refLinkInfos == null || !refLinkInfos.isEmpty();

        if (refLinkInfos != null) {
            for (RefLinkInfo refLinkInfo : refLinkInfos) {
                assert root.getId().equals(refLinkInfo.m_referent) ||
                        vertexIdIsAncestorOf(root.getId(), refLinkInfo.m_referent);
            }
        }

        verifyOutEdges(root);

        deepTraverse(root, new Processor() {

            public boolean run(Vertex vertex, int level) {
                if (vertex == root) {
                    return true;
                }

                verifyVertex(vertex);

                //回收站里的节点只有一个入边，因为所有的引用边都删除了
                Iterable<Edge> inEdges = vertex.getEdges(Direction.IN);

                Iterator iterator = inEdges.iterator();
                assert iterator.hasNext() == true;
                iterator.next();
                assert iterator.hasNext() == false;

                return true;
            }
        });
    }

    static class OutEdgeIdPair implements Comparable<OutEdgeIdPair>{
        //final成员，保证不被修改。这样OutEdgeIdPair的容器不需要深层拷贝了。
        final String m_innerId;
        Object m_dbId;
        final MindDB m_mindDB;

        OutEdgeIdPair(MindDB mindDb, Object dbId, String innerId) {
            assert mindDb != null;
            assert dbId != null;
            assert innerId != null;

            m_mindDB = mindDb;
            m_dbId = dbId;
            m_innerId = innerId;
        }

        public String toString() {
            return "OutEdgeIdPair:[" + m_dbId.toString() + ", " + m_innerId + "]";
        }

        public int compareTo(OutEdgeIdPair other) {
            return m_innerId.compareTo(other.m_innerId);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (! (other instanceof OutEdgeIdPair)) {
                return false;
            }

            OutEdgeIdPair otherIdPair = (OutEdgeIdPair)other;
            if (m_mindDB != otherIdPair.m_mindDB) {
                return false;
            }

            if (! m_innerId.equals(otherIdPair.m_innerId)) {
                return false;
            }

            if (m_dbId.equals(otherIdPair.m_dbId)) {
                return true;
            }

            //TODO 更新边的id

            return false;
        }

        public boolean belongTo(Edge edge) {
            if (m_dbId.equals(edge.getId())) {
                String edgeInnerId = getOutEdgeInnerId(edge);
                assert m_innerId.equals(edgeInnerId);
                return true;
            }
            return false;
        }
    }
}
