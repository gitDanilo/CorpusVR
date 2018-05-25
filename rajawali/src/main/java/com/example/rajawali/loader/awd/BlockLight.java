package com.example.rajawali.loader.awd;

import com.example.rajawali.loader.LoaderAWD.AWDLittleEndianDataInputStream;
import com.example.rajawali.loader.LoaderAWD.BlockHeader;
import com.example.rajawali.loader.awd.exceptions.NotImplementedParsingException;

/**
 * 
 * @author Ian Thomas (toxicbakery@gmail.com)
 * 
 */
public class BlockLight extends ABlockParser {

	public void parseBlock(AWDLittleEndianDataInputStream dis, BlockHeader blockHeader) throws Exception {
		throw new NotImplementedParsingException();
	}

}
