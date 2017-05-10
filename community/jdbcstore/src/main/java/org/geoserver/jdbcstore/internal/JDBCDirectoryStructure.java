/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.jdbcstore.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.geoserver.platform.resource.Paths;
import org.geoserver.jdbcstore.internal.JDBCQueryHelper.*;

import static org.geoserver.jdbcstore.internal.JDBCQueryHelper.*;

/**
 * 
 * Handles database access & ORM mapping of directory structure
 *
 * @author Kevin Smith, Boundless
 * @author Niels Charlier
 *
 *
 */
public class JDBCDirectoryStructure {

    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(JDBCDirectoryStructure.class);

    protected final static String TABLE_RESOURCES = "resources";

    protected final static Field<Integer> OID = new Field<Integer>("oid", "oid", TYPE_INT);

    protected final static Field<String> NAME = new Field<String>("name", "name", TYPE_STRING);

    protected final static Field<Integer> PARENT = new Field<Integer>("parent", "parent", TYPE_INT);

    protected final static Field<Timestamp> LAST_MODIFIED = new Field<Timestamp>("last_modified", "last_modified", TYPE_TIMESTAMP);

    protected final static Field<InputStream> CONTENT = new Field<InputStream>("content", "content", TYPE_BLOB);

    protected final static Field<Boolean> DIRECTORY = new Field<Boolean>("directory", "content IS NULL AS directory", TYPE_BOOLEAN);

    private JDBCResourceStoreProperties config;

    private JDBCQueryHelper helper;

    /**
     * Resource/Directory entry in the database.
     *
     */
    public class Entry {
        private final List<String> path;

        public Entry(List<String> path) {
            this.path = new ArrayList<String>(path);
        }

        public Entry(List<String> parent, String child) {
            path = new ArrayList<String>(parent);
            path.add(child);
        }

        public Entry(String path) {
            this.path = Paths.names(path);
        }

        @SuppressWarnings("unchecked")
        protected <T> T getValue(Field<T> prop) {
            Map<String, Object> record = helper.selectQuery(TABLE_RESOURCES, new PathSelector(path),
                    prop);
            return record == null ? null : (T) record.get(prop.getFieldName());
        }

        public Integer getOid() {
            return getValue(OID);
        }

        public Entry getParent() {
            return path.isEmpty() ? null : new Entry(path.subList(0, path.size() - 1));
        }

        public Boolean isDirectory() {
            return getValue(DIRECTORY); // null safe
        }

        public String getName() {
            return path.isEmpty() ? null : path.get(path.size() - 1);
        }

        public List<String> getPath() {
            return Collections.unmodifiableList(path);
        }

        public Timestamp getLastModified() {
            return getValue(LAST_MODIFIED);
        }

        public List<Entry> getChildren() {
            List<Entry> list = new ArrayList<Entry>();
            Integer oid = getOid();
            if (oid != null) {
                for (Map<String, Object> result : helper.multiSelectQuery(TABLE_RESOURCES,
                        new FieldSelector<Integer>(PARENT, oid), NAME)) {
                    list.add(new Entry(path, (String) result.get(NAME.getFieldName())));
                }
            }
            return list;
        }

        public boolean delete() {
            Integer oid = getOid();
            if (oid == null) {
                LOGGER.warning("Attempting to delete undefined entry " + toString());
                return false;
            }

            if (!deleteChildren(oid)) {
                LOGGER.warning("Delete operation failed or incomplete for entry " + toString());
                return false;
            }

            if (helper.deleteQuery(TABLE_RESOURCES, new FieldSelector<Integer>(OID, oid)) <= 0) {
                LOGGER.warning("Delete operation failed or incomplete for entry " + toString());
                return false;
            }
            return true;
        }

        public boolean renameTo(Entry dest) {
            Integer oid = getOid();

            if (oid == null) {
                LOGGER.warning("Attempted to rename undefined entry: " + toString());
                return false;
            }

            Integer destParentOid = dest.getValue(PARENT);
            if (destParentOid != null) {
                if (config.isDeleteDestinationOnRename()) {
                    if (!dest.delete()) {
                        LOGGER.warning("Rename operation failed for entry " + toString()
                                + ": unable to delete destination of rename operation.");
                        return false;
                    }
                } else {
                    LOGGER.warning("Rename operation failed for entry " + toString()
                            + ": destination of rename operation is defined.");
                    return false;
                }
            } else {
                Entry destParent = dest.getParent();
                try {
                    destParent.createDirectory();
                } catch (IllegalStateException e) {
                    LOGGER.log(Level.WARNING, "Rename operation failed for entry " + toString()
                            + ": could not create parent directory.", e);
                    return false;
                }
                destParentOid = destParent.getOid();
            }

            if (helper.updateQuery(TABLE_RESOURCES, new FieldSelector<Integer>(OID, oid),
                    new Assignment<String>(NAME, dest.getName()), new Assignment<Integer>(PARENT,
                            destParentOid)) <= 0) {
                LOGGER.warning("Unable to perform rename operation for entry " + toString());
                return false;
            }
            ;

            return true;
        }

        public InputStream getContent() {
            InputStream is = helper.blobQuery(TABLE_RESOURCES, new PathSelector(path), CONTENT);
            if (is == null) {
                throw new IllegalStateException("Could not find content for entry " + toString());
            }
            return is;
        }

        public void setContent(InputStream is) {
            if (helper.updateQuery(TABLE_RESOURCES, new PathSelector(path),
                    new Assignment<InputStream>(CONTENT, is), new Assignment<Timestamp>(
                            LAST_MODIFIED, new Timestamp(new java.util.Date().getTime()))) <= 0) {
                LOGGER.warning("Unable to write content to entry " + toString());
            }
        }

        public void createDirectory() {
            int parentOid = 0;
            for (String name : path) {
                Map<String, Object> record = helper.selectQuery(TABLE_RESOURCES, new ChildSelector(
                        parentOid, name), OID, DIRECTORY);

                if (record == null) {
                    parentOid = helper.insertQuery(TABLE_RESOURCES, new Assignment<String>(NAME,
                            name), new Assignment<Integer>(PARENT, parentOid));
                } else {
                    if (!(Boolean) record.get(DIRECTORY.getFieldName())) {
                        throw new IllegalStateException("Could not create directory at "
                                + toString()
                                + ": one of its parents exists and is not a directory.");
                    }

                    parentOid = (Integer) record.get(OID.getFieldName());
                }
            }
        }

        public boolean createResource() {
            Boolean dir = isDirectory();

            if (dir != null) {
                if (dir) {
                    throw new IllegalStateException("Could not create resource at " + toString()
                            + ": already a directory.");
                } else {
                    return false;
                }
            }

            Entry parent = getParent();
            try {
                parent.createDirectory();
            } catch (IllegalStateException e) {
                throw new IllegalStateException("Could not create resource at " + toString()
                        + ": could not create parent directory.", e);
            }

            ByteArrayInputStream is = new ByteArrayInputStream(new byte[0]);
            Integer oid = helper.insertQuery(TABLE_RESOURCES,
                    new Assignment<String>(NAME, getName()),
                    new Assignment<Integer>(PARENT, parent.getOid()), new Assignment<InputStream>(
                            CONTENT, is));
            try {
                is.close();
            } catch (IOException e) {
                LOGGER.warning("Failed to close stream: " + toString());
            }

            if (oid == null) {
                throw new IllegalStateException("Did not get OID for new entry " + toString());
            }

            return true;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) { // no leading slash
                    buf.append("/");
                }
                buf.append(path.get(i));
            }
            return buf.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getStructure().hashCode();
            result = prime * result + getPath().hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry other = (Entry) obj;
            return getStructure().equals(other.getStructure()) && getPath().equals(other.getPath());
        }

        protected JDBCDirectoryStructure getStructure() {
            return JDBCDirectoryStructure.this;
        }
    }

    public JDBCDirectoryStructure(DataSource ds, JDBCResourceStoreProperties config) {
        this.helper = new JDBCQueryHelper(ds);
        this.config = config;

        if (config.isInitDb()) {
            LOGGER.log(Level.INFO, "Initializing Resource Store Database.");
            helper.runScript(config.getInitScript());
            config.setInitDb(false);
            try {
                config.save();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Unable to save ResourceStore configuration", e);
            }
        }
    }

    public Entry createEntry(String path) {
        return new Entry(path);
    }

    public JDBCResourceStoreProperties getConfig() {
        return config;
    }

    // ------------------------------ private helper methods & classes

    private boolean deleteChildren(Integer oid) {
        // get ids of children
        List<Integer> children = new ArrayList<Integer>();
        for (Map<String, Object> result : helper.multiSelectQuery(TABLE_RESOURCES,
                new FieldSelector<Integer>(PARENT, oid), OID)) {
            children.add((Integer) result.get(OID.getFieldName()));
        }

        if (children.size() > 0) {
            // recursively apply to children
            for (Integer child : children) {
                if (!deleteChildren(child)) {
                    return false;
                }
            }

            // delete all children in one go
            if (helper.deleteQuery(TABLE_RESOURCES, new FieldSelector<Integer>(PARENT, oid)) < children
                    .size()) {
                return false;
            }
        }
        return true;
    }

    private static class PathSelector implements Selector {
        private List<String> path;

        private int contextOid;

        public PathSelector(List<String> path) {
            this(0, path);
        }

        public PathSelector(int contextOid, List<String> path) {
            this.path = path;
            this.contextOid = contextOid;
        }

        private void oidQuery(QueryBuilder builder, int i) {
            assert (i >= 0);
            assert (i < path.size());

            if (i > 0) {
                builder.append("SELECT oid FROM " + TABLE_RESOURCES + " WHERE parent=(");
                oidQuery(builder, i - 1);
                builder.append(") and name=? ");
                builder.addParameter(new Parameter<String>(TYPE_STRING, path.get(i)));
            } else {
                builder.append("SELECT oid FROM " + TABLE_RESOURCES + " WHERE parent=? and name=? ");
                builder.addParameter(new Parameter<Integer>(TYPE_INT, contextOid));
                builder.addParameter(new Parameter<String>(TYPE_STRING, path.get(i)));
            }
        }

        @Override
        public QueryBuilder appendCondition(QueryBuilder qb) {
            if (path.size() > 0) {
                qb.append("oid = (");
                oidQuery(qb, path.size() - 1);
                qb.append(")");
            } else {
                qb.append("oid = ?");
                qb.addParameter(new Parameter<Integer>(TYPE_INT, contextOid));
            }
            return qb;
        }
    }

    private static class ChildSelector implements Selector {
        private String name;

        private int parentOid;

        public ChildSelector(int parentOid, String name) {
            this.name = name;
            this.parentOid = parentOid;
        }

        @Override
        public QueryBuilder appendCondition(QueryBuilder qb) {
            qb.append("parent=? and name=? ");
            qb.addParameter(new Parameter<Integer>(TYPE_INT, parentOid));
            qb.addParameter(new Parameter<String>(TYPE_STRING, name));
            return qb;
        }

    }

}
