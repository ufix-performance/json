package json;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class JSON {
    
    private static final int DEFAULT_FIELDS = 512;
    
    private static final Charset DEFAULT_CHAR_SET = StandardCharsets.US_ASCII;

    private Charset charset;

    private boolean isArray;

    private Elements data;
    
    private int head;
    
    private int tail;
    
    private int numItems;
    
    private JSON(JSON json, boolean isArray) {
	data = json.getData();
	charset = json.getCharset();
	head = -1;
	numItems = 0;
	this.isArray = isArray;
    }
    
    public JSON() {
	isArray = false;
	init(DEFAULT_FIELDS);
    }
    
    public JSON(int size) {
	isArray = false;
	init(size);
    }
    
    public JSON(int size, boolean isArray) {
        this.isArray = isArray;
	init(size);
    }

    public JSON(int size, int bufSize) {
	isArray = false;
        init(size);
	data.initBuf(bufSize);
    }

    private void init(int size) {
	data = new Elements(size);
	head = -1;
	numItems = 0;
	charset = DEFAULT_CHAR_SET;
    }
    
    private static boolean isSpace(byte val) {
	return (val <= (byte) ' ') && (val == (byte) ' ' || val == (byte) '\n' || val == (byte) '\r'|| val == (byte) '\t');
    }

    private static int copy(String value, byte[] buf, int offset, Charset charset) {
	byte[] array = value.getBytes(charset);
	
	System.arraycopy(array, 0, buf, offset, array.length);
	
	offset += array.length;
	
	return offset;
    }
    
    private static int copyKey(String key, byte[] buf, int offset) {
	buf[offset++] = (byte) '\"';
	offset = copy(key, buf, offset, DEFAULT_CHAR_SET);
	buf[offset++] = (byte) '\"';
	buf[offset++] = (byte) ':';
	return offset;
    }
    
    private int copyValue(Object val, byte[] buf, int offset) {
	if (val instanceof JSON) {
	    JSON jsonVal = (JSON) val;
	    if (jsonVal.isArray()) {
		buf[offset++] = (byte) '[';
		offset += jsonVal.toBytes(buf, offset, false);
		buf[offset++] = (byte) ']';
	    } else {
		offset += jsonVal.toBytes(buf, offset);
	    }
	} else {
	    if (val instanceof String) {
		buf[offset++] = (byte) '\"';
		offset = copy((String) val, buf, offset, charset);
		buf[offset++] = (byte) '\"';
	    } else if (val instanceof Character) {
		buf[offset++] = (byte) '\"';                
                buf[offset++] = (byte) ((char) val);
                buf[offset++] = (byte) '\"';
	    } else {
		offset = copy(String.valueOf(val), buf, offset, charset);
	    }
	}
	return offset;
    }
    
    public final Elements getData() {
	return data;
    }

    public final Element[] getElements() {
	return data.getElements();
    }
    
    public final boolean isArray() {
	return isArray; 
    }

    public final void reset() {
	if (head == 0) {
	    data.reset();
	}
	head = -1;
	numItems = 0;
    }
    
    public final void initMap() {
	data.initMap();
    }

    public final void put(String key, Object value) {
	
	if (isArray) key = null;

	int index = data.put(key, value, head, tail);
	
     	if (head == -1) {
	    head = index;
	} 
	
	tail = index;
	numItems++;
    }
    
    public final void setCharset(Charset charset) {
	this.charset = charset;
    }

    public final Charset getCharset() {
	return charset;
    }

    public final void put(Object value) {
	put(null, value);
    }
    
    public final Object get(String key) {
	return data.get(key, head, numItems);
    }

    public final String getString(String key) {
	Object object = get(key);
	if (object != null) return object.toString();
	return null;
    }
    
    public final JSON getArray(String key) {
	JSON json;
        Object child = get(key);
	
	if (child == null) {
	    return new JSON(0, true);
	}
	
	if (child instanceof JSON) {
            json = (JSON) child;
            if (json.isArray()) {
                return json;
            }
        }
	
        json = new JSON(1, true);
        json.put(child);
        return json;
    }

    public final JSON createJSON(String key) {
	JSON json = new JSON(this, false);
	put(key, json);
	return json;
    }
    
    public final JSON createJSON() {
        return createJSON(null);
    }

    public final JSON createJSONArray(String key) {
        JSON json = new JSON(this, true);
        put(key, json);
        return json;
    }

    private int toBytes(byte[] buf, int offset, boolean isHeaderIncluded) {
	int startPos = offset;
	
	if (isHeaderIncluded) {
	    if (isArray) {
		buf[offset++] = (byte) '[';
	    } else {
		buf[offset++] = (byte) '{';
	    }
	}

	int pos = head;
	
	Element[] elements = data.getElements();

	for (int i = 0; i < numItems; i++) {
	    Element element = elements[pos];
	    pos = element.next;
	    if (element.value == null) continue;
	    
	    if (i > 0) {
		buf[offset++] = (byte) ',';
	    } 
	    
	    if (element.key != null) {
		offset = copyKey(element.key, buf, offset);
	    }
	    
	    offset = copyValue(element.value, buf, offset);
	}
	if (isHeaderIncluded) {
	    if (isArray) {
		buf[offset++] = (byte) ']';
	    } else {
		buf[offset++] = (byte) '}';
	    }
	}
	return offset - startPos;
    }
    
    private int findNextDelim(byte[] buf, int startPos, int endPos, byte delim) {
	for (int i = startPos; i < endPos; i++) {
	    if (buf[i] == delim) return i;
	}
	return -1;
    }
    
    private int findEndValuePos(byte[] buf, int startPos, int endPos) {
	if (buf[startPos] == (byte) '{') {
	    return findEndJSONValuePos(buf, startPos, endPos);
	} else if (buf[startPos] == (byte) '[') {
	    return findEndJSONArrayValuePos(buf, startPos, endPos);
	} else {
	    
	    boolean isOutsideQuotationMark = true;
	    
	    for (int i = startPos; i < endPos; i++) {
		
		if (buf[i] == (byte) '\"') {
		    isOutsideQuotationMark = !isOutsideQuotationMark;
		    continue;
		}

		if (isOutsideQuotationMark && (buf[i] == (byte) ',' || buf[i] == (byte) '}' || buf[i] == (byte) ']')) return i;
	    }    
	    return -1;
	}
    }

    private int findValuePos(byte[] buf, int startPos, int endPos, byte open, byte close) {
	int counter = 1;
	for (int i = startPos + 1; i < endPos; i++) {
	    if (buf[i] == close) {
		counter--;
	    } else if (buf[i] == open) {
		counter++;
	    }
	    if (counter == 0) {
		return (i + 1);
	    }
	}
	return -1;
    }
    
    private int findEndJSONValuePos(byte[] buf, int startPos, int endPos) {
	return findValuePos(buf, startPos, endPos, (byte) '{', (byte) '}');
    }

    private int findEndJSONArrayValuePos(byte[] buf, int startPos, int endPos) {
	return findValuePos(buf, startPos, endPos, (byte) '[', (byte) ']');
    }

    private String getString(byte[] buf, int startPos, int endPos) {
	while (buf[startPos] == (byte) ' ') {
	    startPos++;
	}
	
	while (buf[--endPos] == (byte) ' ') {
	}

	if (buf[startPos] == (byte) '\"') {
	    return new String(buf, startPos + 1, endPos - startPos - 1, charset);
	} else {
	    return new String(buf, startPos, endPos + 1 - startPos, charset);
	}
    }
    
    private boolean parseArray(byte[] buf, int offset, int length) {
	int startPos = offset;
        int endPos = offset + length;

	while (true) {
	    startPos++;
	    
	    while (startPos < endPos && isSpace(buf[startPos])) {
                startPos++;
            }

	    if (startPos >= endPos - 1) return true;
	    
	    int pos = findEndValuePos(buf, startPos, endPos);
	    if (pos < 0) return false;
	    
	    if (buf[startPos] == (byte) '{') {
		JSON jsonChild = createJSON(null);
		if (!jsonChild.parse(buf, startPos, pos - startPos)) {
		    return false;
		}
	    } else if (buf[startPos] == (byte) '[') {
		JSON jsonChild = createJSONArray(null);
		if (!jsonChild.parse(buf, startPos, pos - startPos)) {
		    return false;
		}
	    } else {
		put(null, getString(buf, startPos, pos));
	    }
	    startPos = pos;
	}
    }

    private boolean parseJSON(byte[] buf, int offset, int length) {
	int startPos = offset;
	int endPos = offset + length;
	
	while (true) {
	    startPos++;
	    
	    while (startPos < endPos && isSpace(buf[startPos])) {
		startPos++;
	    }

	    if (startPos >= endPos - 1) {
		return true;
	    }

	    int pos = findNextDelim(buf, startPos, endPos, (byte) ':'); 
	    if (pos < 0) {
		return false;
	    }
	    
	    String key = getString(buf, startPos, pos);
	    startPos = pos + 1;
	    
	    while (startPos < endPos && isSpace(buf[startPos])) {
                startPos++;
            }

	    pos = findEndValuePos(buf, startPos, endPos);
	    if (pos < 0) {
		return false;
	    }

	    if (buf[startPos] == (byte) '{') {
		JSON jsonChild = createJSON(key);
		if (!jsonChild.parse(buf, startPos, pos - startPos)) return false;
	    } else if (buf[startPos] == (byte) '[') {
		JSON jsonChild = createJSONArray(key);
		if (!jsonChild.parse(buf, startPos, pos - startPos)) return false;
	    } else {
		put(key, getString(buf, startPos, pos));
	    }
	    startPos = pos;
	}
    }
    
    public final boolean parse(byte[] buf, int offset, int length) {
	int numItemsBeforeParsing = numItems;
	
	initMap();

	int endPos = offset + length;
	while (offset < endPos && isSpace(buf[offset])) {
	    offset++;
	}
	
	while (isSpace(buf[endPos - 1])) {
	    endPos--;
	}
	
	length = endPos - offset;

	if (buf[offset] == (byte) '{') {
	    if (!parseJSON(buf, offset, length)) {
		numItems = numItemsBeforeParsing;
		return false;
	    } 
	} else if (buf[offset] == (byte) '[') {
	    if (!parseArray(buf, offset, length)) {
		numItems = numItemsBeforeParsing;
		return false;
	    }
	    isArray = true;
	} else {
	    return false;
	}
	return true;
    }

    public final boolean parse(String buf) {
	byte[] bytes = buf.getBytes();
	return parse(bytes, 0, bytes.length);
    }

    public final int getLength() {
	return numItems;
    }

    public final Element getHead() {
	if (numItems == 0) return null;
	return data.getElement(head);
    }
    
    public final Element next(Element element) {
	int pos = element.next;
	return data.getElement(pos);
    }
    
    public final int toBytes(byte[] buf, int offset) {
	return toBytes(buf, offset, true);
    }
    
    public final String toString(byte[] buf, int offset) {
	int length = toBytes(buf, offset);
	return new String(buf, offset, length, charset);
    }

    public final String toString() {
	while (true) {
	    try {
		byte[] buf = data.getBuf();
		return toString(buf, 0);
	    } catch (ArrayIndexOutOfBoundsException e) {
		data.doubleBufSize();
	    }
	}
    }
}