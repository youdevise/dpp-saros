package de.fu_berlin.inf.dpp.activities;

import org.eclipse.core.runtime.IPath;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

import de.fu_berlin.inf.dpp.concurrent.jupiter.Timestamp;
import de.fu_berlin.inf.dpp.net.JID;

/**
 * A checksum activity is used to communicate checksums from the host to the
 * clients.
 * 
 * A checksum activity always relates to a certain file (given a path) and
 * contains the hash and length of the file.
 * 
 * To indicate that a file is missing on the host NON_EXISTING_DOC is used.
 * 
 * A checksum activity also may contain a JupiterTimestamp to indicate at which
 * point of time the checksum was created. A remote user can use this
 * information to see whether the checksum can be used to check for consistency
 * or whether the local user has already written additional text which
 * invalidates the checksum.
 */
@XStreamAlias("fileActivity")
public class ChecksumActivity extends AbstractActivity {

    /**
     * Constant used for representing a missing file
     */
    public static final int NON_EXISTING_DOC = -1;

    @XStreamAsAttribute
    public IPath path;

    @XStreamAsAttribute
    public long hash;

    @XStreamAsAttribute
    public long length;

    @XStreamAsAttribute
    public Timestamp jupiterTimestamp;

    /**
     * Constructor for a ChecksumActivity with no jupiterTimestamp set (such is
     * used when communicating with users which are observers)
     */
    public ChecksumActivity(JID source, IPath path, long hash, long length) {
        super(source);
        this.path = path;
        this.hash = hash;
        this.length = length;
        this.jupiterTimestamp = null;
    }

    /**
     * Constructor for checksum activities including a Timestamp (for users
     * which are drivers)
     */
    public ChecksumActivity(JID source, IPath path, long hash, long length,
        Timestamp jupiterTimestamp) {
        super(source);
        this.path = path;
        this.hash = hash;
        this.length = length;
        this.jupiterTimestamp = jupiterTimestamp;
    }

    /**
     * Create a ChecksumActivity which indicates that the file is missing on the
     * host.
     */
    public static ChecksumActivity missing(JID source, IPath path) {
        return new ChecksumActivity(source, path, NON_EXISTING_DOC,
            NON_EXISTING_DOC);
    }

    /**
     * Returns a new checksum activity which is identical to this activity, but
     * has the timestamp set to the given value.
     */
    public ChecksumActivity withTimestamp(Timestamp jupiterTimestamp) {
        return new ChecksumActivity(source, path, hash, length,
            jupiterTimestamp);
    }

    /**
     * Returns the path this checksum is about
     */
    public IPath getPath() {
        return this.path;
    }

    @Override
    public String toString() {
        return "Checksum(path:" + this.path + ", hash:" + hash + ", length:"
            + length + ")";
    }

    public boolean dispatch(IActivityConsumer consumer) {
        return consumer.consume(this);
    }

    public void dispatch(IActivityReceiver receiver) {
        receiver.receive(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (int) (hash ^ (hash >>> 32));
        result = prime * result + (int) (length ^ (length >>> 32));
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ChecksumActivity other = (ChecksumActivity) obj;
        if (hash != other.hash)
            return false;
        if (length != other.length)
            return false;
        if (path == null) {
            if (other.path != null)
                return false;
        } else if (!path.equals(other.path))
            return false;
        return true;
    }

    public Timestamp getTimestamp() {
        return jupiterTimestamp;
    }

    public long getLength() {
        return length;
    }

    public long getHash() {
        return hash;
    }

    public boolean existsFile() {
        return !(this.length == NON_EXISTING_DOC && this.hash == NON_EXISTING_DOC);
    }
}