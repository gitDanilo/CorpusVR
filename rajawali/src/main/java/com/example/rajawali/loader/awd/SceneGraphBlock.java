package com.example.rajawali.loader.awd;

import com.example.rajawali.loader.LoaderAWD.AWDLittleEndianDataInputStream;
import com.example.rajawali.loader.LoaderAWD.BlockHeader;
import com.example.rajawali.loader.ParsingException;
import com.example.rajawali.math.Matrix4;
import com.example.rajawali.util.RajLog;

import java.io.IOException;

/**
 * @author Ian Thomas (toxicbakery@gmail.com)
 */
public class SceneGraphBlock {

    public final Matrix4 transformMatrix = new Matrix4();

    public int parentID;
    public String lookupName;

    public void readGraphData(BlockHeader blockHeader, AWDLittleEndianDataInputStream awddis) throws IOException,
            ParsingException {
        // parent id, reference to previously defined object
        parentID = awddis.readInt();

        // Transformation matrix
        awddis.readMatrix3D(transformMatrix, blockHeader.globalPrecisionMatrix, true);

        // Lookup name
        lookupName = awddis.readVarString();
        if (RajLog.isDebugEnabled())
            RajLog.d("  Lookup Name: " + lookupName);
    }

}
