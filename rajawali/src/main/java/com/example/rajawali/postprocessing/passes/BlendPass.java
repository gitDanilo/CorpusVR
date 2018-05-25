/**
 * Copyright 2013 Dennis Ippel
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.example.rajawali.postprocessing.passes;


import com.example.rajawali.R;

import com.example.rajawali.materials.Material;
import com.example.rajawali.materials.textures.ATexture;


public class BlendPass extends com.example.rajawali.postprocessing.passes.EffectPass
{
    protected ATexture mBlendTexture;

    public static enum BlendMode {
        ADD, SCREEN
    }

    public BlendPass(BlendMode blendMode, ATexture blendTexture) {
        super();
        createMaterial(R.raw.minimal_vertex_shader, getFragmentShader(blendMode));
        mBlendTexture = blendTexture;
    }

    @Override
    public void setMaterial(Material material) {
        super.setMaterial(material);
        material.setTextureHandleForName(PARAM_BLEND_TEXTURE);
    }

    protected int getFragmentShader(BlendMode blendMode) {
        switch (blendMode) {
            case ADD:
                return R.raw.blend_add_fragment_shader;
            case SCREEN:
                return R.raw.blend_screen_fragment_shader;
            default:
                return R.raw.blend_add_fragment_shader;
        }
    }

    public void setShaderParams() {
        super.setShaderParams();
        mMaterial.bindTextureByName(PARAM_BLEND_TEXTURE, 1, mBlendTexture);
    }
}
