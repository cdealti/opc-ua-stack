package com.inductiveautomation.opcua.stack.core.serialization.binary;

import javax.annotation.Nonnull;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.inductiveautomation.opcua.stack.core.StatusCodes;
import com.inductiveautomation.opcua.stack.core.UaSerializationException;
import com.inductiveautomation.opcua.stack.core.channel.ChannelConfig;
import com.inductiveautomation.opcua.stack.core.serialization.DelegateRegistry;
import com.inductiveautomation.opcua.stack.core.serialization.EncoderDelegate;
import com.inductiveautomation.opcua.stack.core.serialization.UaEncoder;
import com.inductiveautomation.opcua.stack.core.serialization.UaSerializable;
import com.inductiveautomation.opcua.stack.core.serialization.UaStructure;
import com.inductiveautomation.opcua.stack.core.types.builtin.ByteString;
import com.inductiveautomation.opcua.stack.core.types.builtin.DataValue;
import com.inductiveautomation.opcua.stack.core.types.builtin.DateTime;
import com.inductiveautomation.opcua.stack.core.types.builtin.DiagnosticInfo;
import com.inductiveautomation.opcua.stack.core.types.builtin.ExpandedNodeId;
import com.inductiveautomation.opcua.stack.core.types.builtin.ExtensionObject;
import com.inductiveautomation.opcua.stack.core.types.builtin.LocalizedText;
import com.inductiveautomation.opcua.stack.core.types.builtin.NodeId;
import com.inductiveautomation.opcua.stack.core.types.builtin.QualifiedName;
import com.inductiveautomation.opcua.stack.core.types.builtin.StatusCode;
import com.inductiveautomation.opcua.stack.core.types.builtin.Variant;
import com.inductiveautomation.opcua.stack.core.types.builtin.XmlElement;
import com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.UByte;
import com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.UInteger;
import com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.ULong;
import com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.UShort;
import com.inductiveautomation.opcua.stack.core.types.enumerated.IdType;
import com.inductiveautomation.opcua.stack.core.util.ArrayUtil;
import com.inductiveautomation.opcua.stack.core.util.TypeUtil;
import com.inductiveautomation.opcua.stack.core.util.annotations.UBytePrimitive;
import com.inductiveautomation.opcua.stack.core.util.annotations.UInt16Primitive;
import com.inductiveautomation.opcua.stack.core.util.annotations.UInt32Primitive;
import com.inductiveautomation.opcua.stack.core.util.annotations.UInt64Primitive;
import io.netty.buffer.ByteBuf;

public class BinaryEncoder implements UaEncoder {

    private volatile ByteBuf buffer;

    private final int maxArrayLength;
    private final int maxStringLength;

    public BinaryEncoder() {
        this(ChannelConfig.DEFAULT_MAX_ARRAY_LENGTH, ChannelConfig.DEFAULT_MAX_STRING_LENGTH);
    }

    public BinaryEncoder(int maxArrayLength, int maxStringLength) {
        this.maxArrayLength = maxArrayLength;
        this.maxStringLength = maxStringLength;
    }

    public BinaryEncoder setBuffer(ByteBuf buffer) {
        this.buffer = buffer;
        return this;
    }

	public ByteBuf getBuffer() {
		return buffer;
	}

	@Override
    public void encodeBoolean(String field, Boolean value) {
        buffer.writeBoolean(value);
    }

    @Override
    public void encodeSByte(String field, Byte value) {
        buffer.writeByte(value);
    }

    @Override
    public void encodeInt16(String field, Short value) {
        buffer.writeShort(value);
    }

    @Override
    public void encodeInt32(String field, Integer value) {
        buffer.writeInt(value);
    }

    @Override
    public void encodeInt64(String field, Long value) {
        buffer.writeLong(value);
    }

    @Override
    public void encodeByte(String field, @UBytePrimitive Short value) {
        buffer.writeByte(value);
    }

    @Override
    public void encodeByte(String field, UByte value) throws UaSerializationException {
        encodeByte(field, value.shortValue());
    }

    @Override
    public void encodeUInt16(String field, @UInt16Primitive Integer value) {
        buffer.writeShort(value);
    }

    @Override
    public void encodeUInt16(String field, UShort value) throws UaSerializationException {
        encodeUInt16(field, value.intValue());
    }

    @Override
    public void encodeUInt32(String field, @UInt32Primitive Long value) {
        buffer.writeInt(value.intValue());
    }

    @Override
    public void encodeUInt32(String field, UInteger value) throws UaSerializationException {
        encodeUInt32(field, value.longValue());
    }

    @Override
    public void encodeUInt64(String field, @UInt64Primitive Long value) {
        buffer.writeLong(value);
    }

    @Override
    public void encodeUInt64(String field, ULong value) throws UaSerializationException {
        encodeUInt64(field, value.longValue());
    }

    @Override
    public void encodeFloat(String field, Float value) {
        buffer.writeFloat(value);
    }

    @Override
    public void encodeDouble(String field, Double value) {
        buffer.writeDouble(value);
    }

    @Override
    public void encodeString(String field, String value) throws UaSerializationException {
        if (value == null) {
            buffer.writeInt(-1);
        } else {
            if (value.length() > maxStringLength) {
                throw new UaSerializationException(StatusCodes.Bad_EncodingLimitsExceeded,
                        "max string length exceeded");
            }

            try {
                // Record the current index and write a placeholder for the length.
                int lengthIndex = buffer.writerIndex();
                buffer.writeInt(0x42424242);

                // Write the string bytes.
                int indexBefore = buffer.writerIndex();
                buffer.writeBytes(value.getBytes("UTF-8"));
                int indexAfter = buffer.writerIndex();
                int bytesWritten = indexAfter - indexBefore;

                // Go back and update the length.
                buffer.writerIndex(lengthIndex);
                buffer.writeInt(bytesWritten);

                // Return to where we were after writing the string.
                buffer.writerIndex(indexAfter);
            } catch (UnsupportedEncodingException e) {
                throw new UaSerializationException(StatusCodes.Bad_EncodingError, e);
            }
        }
    }

    @Override
    public void encodeDateTime(String field, DateTime value) {
        if (value == null) {
            buffer.writeLong(0L);
        } else {
            buffer.writeLong(value.getUtcTime());
        }
    }

    @Override
    public void encodeGuid(String field, UUID value) {
        if (value == null) {
            buffer.writeZero(16);
        } else {
            long msb = value.getMostSignificantBits();
            long lsb = value.getLeastSignificantBits();

            buffer.writeInt((int) (msb >>> 32));
            buffer.writeShort((int) (msb >>> 16) & 0xFFFF);
            buffer.writeShort((int) (msb) & 0xFFFF);

            buffer.order(ByteOrder.BIG_ENDIAN).writeLong(lsb);
        }
    }

    @Override
    public void encodeByteString(String field, ByteString value) {
        if (value == null || value.bytes() == null) {
            buffer.writeInt(-1);
        } else {
            byte[] bytes = value.bytes();

            assert (bytes != null);

            buffer.writeInt(bytes.length);
            buffer.writeBytes(bytes);
        }
    }

    @Override
    public void encodeXmlElement(String field, XmlElement value) throws UaSerializationException {
        if (value.getFragment() == null) {
            buffer.writeInt(-1);
        } else {
            try {
                encodeByteString(null, new ByteString(value.getFragment().getBytes("UTF-8")));
            } catch (UnsupportedEncodingException e) {
                throw new UaSerializationException(StatusCodes.Bad_DecodingError, e);
            }
        }
    }

    @Override
    public void encodeNodeId(String field, NodeId value) throws UaSerializationException {
		if (value == null) value = NodeId.NullValue;

        int namespaceIndex = value.getNamespaceIndex().intValue();

        if (value.getType() == IdType.Numeric) {
            UInteger identifier = (UInteger) value.getIdentifier();
            long idv = identifier.longValue();

            if (namespaceIndex == 0 && idv >= 0 && idv <= 255) {
                /* Two-byte format */
                buffer.writeByte(0x00);
                buffer.writeByte((int) idv);
            } else if (namespaceIndex >= 0 && namespaceIndex <= 255 && idv <= 65535) {
                /* Four-byte format */
                buffer.writeByte(0x01);
                buffer.writeByte(namespaceIndex);
                buffer.writeShort((int) idv);
            } else {
                /* Numeric format */
                buffer.writeByte(0x02);
                buffer.writeShort(namespaceIndex);
                buffer.writeInt((int) idv);
            }
        } else if (value.getType() == IdType.String) {
            String identifier = (String) value.getIdentifier();

            buffer.writeByte(0x03);
            buffer.writeShort(namespaceIndex);
            encodeString(null, identifier);
        } else if (value.getType() == IdType.Guid) {
            UUID identifier = (UUID) value.getIdentifier();

            buffer.writeByte(0x04);
            buffer.writeShort(namespaceIndex);
            encodeGuid(null, identifier);
        } else if (value.getType() == IdType.Opaque) {
            ByteString identifier = (ByteString) value.getIdentifier();

            buffer.writeByte(0x05);
            buffer.writeShort(namespaceIndex);
            encodeByteString(null, identifier);
        } else {
            throw new UaSerializationException(StatusCodes.Bad_EncodingError, "invalid identifier: " + value.getIdentifier());
        }
    }

    @Override
    public void encodeExpandedNodeId(String field, ExpandedNodeId value) throws UaSerializationException {
        if (value == null) value = ExpandedNodeId.NullValue;

        int flags = 0;
        String namespaceUri = value.getNamespaceUri();
        long serverIndex = value.getServerIndex();

        if (namespaceUri != null && namespaceUri.length() > 0) {
            flags |= 0x80;
        }

        if (serverIndex > 0) {
            flags |= 0x40;
        }

        int namespaceIndex = value.getNamespaceIndex().intValue();

        if (value.getType() == IdType.Numeric) {
            UInteger identifier = (UInteger) value.getIdentifier();
            long idv = identifier.longValue();

            if (namespaceIndex == 0 && idv >= 0 && idv <= 255) {
                /* Two-byte format */
                buffer.writeByte(flags);
                buffer.writeByte((int) idv);
            } else if (namespaceIndex >= 0 && namespaceIndex <= 255 && idv <= 65535) {
                /* Four-byte format */
                buffer.writeByte(0x01 | flags);
                buffer.writeByte(namespaceIndex);
                buffer.writeShort((int) idv);
            } else {
                /* Numeric format */
                buffer.writeByte(0x02 | flags);
                buffer.writeShort(namespaceIndex);
                buffer.writeInt((int) idv);
            }
        } else if (value.getType() == IdType.String) {
            String identifier = (String) value.getIdentifier();

            buffer.writeByte(0x03 | flags);
            buffer.writeShort(namespaceIndex);
            encodeString(null, identifier);
        } else if (value.getType() == IdType.Guid) {
            UUID identifier = (UUID) value.getIdentifier();

            buffer.writeByte(0x04 | flags);
            buffer.writeShort(namespaceIndex);
            encodeGuid(null, identifier);
        } else if (value.getType() == IdType.Opaque) {
            ByteString identifier = (ByteString) value.getIdentifier();

            buffer.writeByte(0x05 | flags);
            buffer.writeShort(namespaceIndex);
            encodeByteString(null, identifier);
        } else {
            throw new UaSerializationException(StatusCodes.Bad_EncodingError, "invalid identifier: " + value.getIdentifier());
        }

        if (namespaceUri != null && namespaceUri.length() > 0) {
            encodeString(null, namespaceUri);
        }

        if (serverIndex > 0) {
            encodeUInt32(null, serverIndex);
        }
    }

    @Override
    public void encodeStatusCode(String field, StatusCode value) {
        buffer.writeInt(value.getValue());
    }

    @Override
    public void encodeQualifiedName(String field, QualifiedName value) throws UaSerializationException {
        encodeUInt16(null, value.getNamespaceIndex());
        encodeString(null, value.getName());
    }

    @Override
    public void encodeLocalizedText(String field, LocalizedText value) throws UaSerializationException {
        String locale = value.getLocale();
        String text = value.getText();

        int mask = 1 | 2;
        if (locale == null || locale.isEmpty()) {
            mask ^= 1;
        }
        if (text == null || text.isEmpty()) {
            mask ^= 2;
        }

        buffer.writeByte(mask);

        if (locale != null && !locale.isEmpty()) {
            encodeString(null, locale);
        }
        if (text != null && !text.isEmpty()) {
            encodeString(null, text);
        }
    }

    @Override
    public void encodeExtensionObject(String field, ExtensionObject value) throws UaSerializationException {
        if (value == null || value.getObject() == null) {
            encodeNodeId(null, NodeId.NullValue);
            buffer.writeByte(0); // No body is encoded
        } else {
            Object object = value.getObject();

            if (object instanceof UaSerializable) {
                UaSerializable structure = (UaSerializable) object;

                encodeNodeId(null, value.getDataTypeEncodingId());
                buffer.writeByte(1); // Body is binary encoded

                // Record the current index and write a placeholder for the length.
                buffer.markWriterIndex();
                buffer.writeInt(0);

                // Write the body.
                int indexBefore = buffer.writerIndex();
                encodeSerializable(null, structure);
                int indexAfter = buffer.writerIndex();
                int bytesWritten = indexAfter - indexBefore;

                // Go back and update the length.
                buffer.resetWriterIndex();
                buffer.writeInt(bytesWritten);

                // Return to where we were after writing the body.
                buffer.writerIndex(indexAfter);
            } else if (object instanceof ByteString) {
                ByteString byteString = (ByteString) object;

                encodeNodeId(null, value.getDataTypeEncodingId());
                buffer.writeByte(1); // Body is binary encoded

                encodeByteString(null, byteString);
            } else if (object instanceof XmlElement) {
                XmlElement xmlElement = (XmlElement) object;

                encodeNodeId(null, value.getDataTypeEncodingId());
                buffer.writeByte(2);

                encodeXmlElement(null, xmlElement);
            } else {
                throw new UaSerializationException(StatusCodes.Bad_EncodingError, "unexpected object in ExtensionObject: " + object);
            }
        }
    }

    @Override
    public void encodeDataValue(String field, DataValue value) throws UaSerializationException {
        if (value == null) {
            buffer.writeByte(0);
            return;
        }

        int mask = 0x00;

        if (value.getValue() != null) mask |= 0x01;
        if (!StatusCode.Good.equals(value.getStatusCode())) mask |= 0x02;
        if (!DateTime.MinValue.equals(value.getSourceTime())) mask |= 0x04;
        if (!DateTime.MinValue.equals(value.getServerTime())) mask |= 0x08;

        buffer.writeByte(mask);

        if ((mask & 0x01) == 0x01) encodeVariant(null, value.getValue());
        if ((mask & 0x02) == 0x02) encodeStatusCode(null, value.getStatusCode());
        if ((mask & 0x04) == 0x04) encodeDateTime(null, value.getSourceTime());
        if ((mask & 0x08) == 0x08) encodeDateTime(null, value.getServerTime());
    }

    @Override
    public void encodeVariant(String field, Variant variant) throws UaSerializationException {
        Object value = variant.getValue();

        if (value == null) {
            buffer.writeByte(0);
        } else {
            int typeId = TypeUtil.getBuiltinTypeId(getType(value));

            if (value.getClass().isArray()) {
                int[] dimensions = ArrayUtil.getDimensions(value);

                if (dimensions.length == 1) {
                    buffer.writeByte(typeId | 0x80);

                    int length = Array.getLength(value);
                    buffer.writeInt(length);

                    for (int i = 0; i < length; i++) {
                        Object o = Array.get(value, i);
                        encodeBuiltinType(typeId, o);
                    }
                } else {
                    buffer.writeByte(typeId | 0xC0);

                    Object flattened = ArrayUtil.flatten(value);
                    int length = Array.getLength(flattened);
                    buffer.writeInt(length);

                    for (int i = 0; i < length; i++) {
                        Object o = Array.get(flattened, i);
                        encodeBuiltinType(typeId, o);
                    }

                    encodeInt32(null, dimensions.length);
                    for (int dimension : dimensions) {
                        encodeInt32(null, dimension);
                    }
                }
            } else {
                buffer.writeByte(typeId);
                encodeBuiltinType(typeId, value);
            }
        }
    }

    private Class<?> getType(@Nonnull Object o) {
        if (o.getClass().isArray()) {
            return ArrayUtil.getType(o);
        } else {
            return o.getClass();
        }
    }


    @Override
    public void encodeDiagnosticInfo(String field, DiagnosticInfo value) throws UaSerializationException {
        if (value == null) {
            buffer.writeByte(0);
        } else {
            int mask = 0x7F;

            if (value.getSymbolicId() == -1) mask ^= 0x01;
            if (value.getNamespaceUri() == -1) mask ^= 0x02;
            if (value.getLocalizedText() == -1) mask ^= 0x04;
            if (value.getLocale() == -1) mask ^= 0x08;
            if (value.getAdditionalInfo() == null || value.getAdditionalInfo().isEmpty()) mask ^= 0x10;
            if (value.getInnerStatusCode() == null) mask ^= 0x20;
            if (value.getInnerDiagnosticInfo() == null) mask ^= 0x40;

            buffer.writeByte(mask);

            if ((mask & 0x01) == 0x01) encodeInt32(null, value.getSymbolicId());
            if ((mask & 0x02) == 0x02) encodeInt32(null, value.getNamespaceUri());
            if ((mask & 0x04) == 0x04) encodeInt32(null, value.getLocalizedText());
            if ((mask & 0x08) == 0x08) encodeInt32(null, value.getLocale());
            if ((mask & 0x10) == 0x10) encodeString(null, value.getAdditionalInfo());
            if ((mask & 0x20) == 0x20) encodeStatusCode(null, value.getInnerStatusCode());
            if ((mask & 0x40) == 0x40) encodeDiagnosticInfo(null, value.getInnerDiagnosticInfo());
        }
    }

    @Override
    public <T extends UaStructure> void encodeMessage(String field, T message) throws UaSerializationException {
        EncoderDelegate<T> delegate = DelegateRegistry.getEncoder(message.getBinaryEncodingId());

        encodeNodeId(null, message.getBinaryEncodingId());

        delegate.encode(message, this);
    }

    @Override
    public <T extends UaSerializable> void encodeSerializable(String field, T value) throws UaSerializationException {
        EncoderDelegate<T> delegate = DelegateRegistry.getEncoder(value);

        delegate.encode(value, this);
    }

    @Override
    public <T> void encodeArray(String field, T[] values, BiConsumer<String, T> consumer) throws UaSerializationException {
        if (values == null) {
            buffer.writeInt(-1);
        } else {
            if (values.length > maxArrayLength) {
                throw new UaSerializationException(StatusCodes.Bad_EncodingLimitsExceeded,
                        "max array length exceeded");
            }

            encodeInt32(null, values.length);
            for (T t : values) {
                consumer.accept(null, t);
            }
        }
    }

    private void encodeBuiltinType(int typeId, Object value) throws UaSerializationException {
        switch (typeId) {
            case 1: encodeBoolean(null, (Boolean) value); break;
            case 2: encodeSByte(null, (Byte) value); break;
            case 3: encodeByte(null, (UByte) value); break;
            case 4: encodeInt16(null, (Short) value); break;
            case 5: encodeUInt16(null, (UShort) value); break;
            case 6: encodeInt32(null, (Integer) value); break;
            case 7: encodeUInt32(null, (UInteger) value); break;
            case 8: encodeInt64(null, (Long) value); break;
            case 9: encodeUInt64(null, (ULong) value); break;
            case 10: encodeFloat(null, (Float) value); break;
            case 11: encodeDouble(null, (Double) value); break;
            case 12: encodeString(null, (String) value); break;
            case 13: encodeDateTime(null, (DateTime) value); break;
            case 14: encodeGuid(null, (UUID) value); break;
            case 15: encodeByteString(null, (ByteString) value); break;
            case 16: encodeXmlElement(null, (XmlElement) value); break;
            case 17: encodeNodeId(null, (NodeId) value); break;
            case 18: encodeExpandedNodeId(null, (ExpandedNodeId) value); break;
            case 19: encodeStatusCode(null, (StatusCode) value); break;
            case 20: encodeQualifiedName(null, (QualifiedName) value); break;
            case 21: encodeLocalizedText(null, (LocalizedText) value); break;
            case 22: encodeExtensionObject(null, (ExtensionObject) value); break;
            case 23: encodeDataValue(null, (DataValue) value); break;
            case 24: encodeVariant(null, (Variant) value); break;
            case 25: encodeDiagnosticInfo(null, (DiagnosticInfo) value); break;
            default: throw new UaSerializationException(StatusCodes.Bad_DecodingError, "unknown builtin type: " + typeId);
        }
    }

}