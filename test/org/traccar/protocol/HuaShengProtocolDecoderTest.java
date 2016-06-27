package org.traccar.protocol;

import org.junit.Test;
import org.traccar.ProtocolTest;

public class HuaShengProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecode() throws Exception {

        HuaShengProtocolDecoder decoder = new HuaShengProtocolDecoder(new HuaShengProtocol());

        verifyNothing(decoder, binary(
                "C00000007EAA020000000000010001001047315F48312E305F56312E3000030013383632393530303238353334333036000400144C342D56374C673979497A7A2D724A6D0005000501000600084341524400070008434152440008000500000900183839383630303530313931343436313130393134000A0009434D4E4554C0"));

        verifyPosition(decoder, binary(
                "C000000041AA00000000000030C000000031353035323630373538323800ADDCC100226AEF0000000000120005000100151206EF0504E99975002903EB80556492CEC0"));

    }

}
