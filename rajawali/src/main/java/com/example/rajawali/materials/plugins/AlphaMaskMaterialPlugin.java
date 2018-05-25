package com.example.rajawali.materials.plugins;

import com.example.rajawali.materials.Material;
import com.example.rajawali.materials.shaders.AShader;
import com.example.rajawali.materials.shaders.IShaderFragment;

public class AlphaMaskMaterialPlugin implements IMaterialPlugin {
    private float mAlphaThreshold = 0.5f;
    private IShaderFragment mFragmentShader;

    public AlphaMaskMaterialPlugin(float alphaThreshold) {
        mAlphaThreshold = alphaThreshold;
        mFragmentShader = new AlphaMaskShaderFragment();
    }

    @Override
    public Material.PluginInsertLocation getInsertLocation() {
        return Material.PluginInsertLocation.PRE_LIGHTING;
    }

    @Override
    public IShaderFragment getVertexShaderFragment() {
        return null;
    }

    @Override
    public IShaderFragment getFragmentShaderFragment() {
        return mFragmentShader;
    }

    @Override
    public void bindTextures(int nextIndex) { }

    @Override
    public void unbindTextures() { }

    private final class AlphaMaskShaderFragment extends AShader implements IShaderFragment {
        public final static String SHADER_ID = "ALPHA_MASK_FRAGMENT_SHADER_FRAGMENT";

        @Override
        public Material.PluginInsertLocation getInsertLocation() {
            return Material.PluginInsertLocation.PRE_LIGHTING;
        }

        @Override
        public String getShaderId() {
            return SHADER_ID;
        }

        @Override
        public void bindTextures(int nextIndex) {

        }

        @Override
        public void unbindTextures() {

        }

        @Override
        public void main() {
            RVec4 color = (RVec4) getGlobal(DefaultShaderVar.G_COLOR);

            startif(new Condition(color.a(), Operator.LESS_THAN_EQUALS, mAlphaThreshold));
            {
                discard();
            }
            endif();
        }
    }
}
