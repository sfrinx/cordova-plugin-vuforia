/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

package com.mattrayner.vuforia.app;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.widget.Toast;

import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.Vuforia;
import com.mattrayner.vuforia.app.ApplicationSession;
import com.mattrayner.vuforia.app.utils.LoadingDialogHandler;
import com.mattrayner.vuforia.app.utils.Texture;


// The renderer class for the ImageTargets sample.
public class ImageTargetRenderer implements GLSurfaceView.Renderer
{
    private static final String LOGTAG = "ImageTargetRenderer";

    private ApplicationSession vuforiaAppSession;
    private ImageTargets mActivity;

    private Renderer mRenderer;

    boolean mIsActive = false;

    String mTargets = "";


    public ImageTargetRenderer(ImageTargets activity,
        ApplicationSession session, String targets)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        mTargets = targets;
    }


    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;

        // Call our function to render content
        renderFrame();
    }


    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        initRendering();

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();
    }


    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);
    }


    // Function for initializing the renderer.
    private void initRendering()
    {
        mRenderer = Renderer.getInstance();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
            : 1.0f);


        // Hide the Loading Dialog
        mActivity.loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);

    }
    
    //Old render
    public void renderFrame(State state, float[] projectionMatrix)
    {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // handle face culling, we need to detect if we are using reflection
        // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        // Did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();
            printUserData(trackable);
            Matrix44F modelViewMatrix_Vuforia = Tool
                    .convertPose2GLMatrix(result.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

            int textureIndex = trackable.getName().equalsIgnoreCase("stones") ? 0
                    : 1;
            textureIndex = trackable.getName().equalsIgnoreCase("tarmac") ? 2
                    : textureIndex;

            // deal with the modelview and projection matrices
            float[] modelViewProjection = new float[16];

            if (!mActivity.isExtendedTrackingActive()) {
                Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f,
                        OBJECT_SCALE_FLOAT);
                Matrix.scaleM(modelViewMatrix, 0, OBJECT_SCALE_FLOAT,
                        OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
            } else {
                Matrix.rotateM(modelViewMatrix, 0, 90.0f, 1.0f, 0, 0);
                Matrix.scaleM(modelViewMatrix, 0, kBuildingScale,
                        kBuildingScale, kBuildingScale);
            }
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);

            // activate the shader program and bind the vertex/normal/tex coords
            GLES20.glUseProgram(shaderProgramID);

            if (!mActivity.isExtendedTrackingActive()) {
                GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                        false, 0, mTeapot.getVertices());
                GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                        GLES20.GL_FLOAT, false, 0, mTeapot.getTexCoords());

                GLES20.glEnableVertexAttribArray(vertexHandle);
                GLES20.glEnableVertexAttribArray(textureCoordHandle);

                // activate texture 0, bind it, and pass to shader
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                        mTextures.get(textureIndex).mTextureID[0]);
                GLES20.glUniform1i(texSampler2DHandle, 0);

                // pass the model view matrix to the shader
                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                        modelViewProjection, 0);

                // finally draw the teapot
                GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                        mTeapot.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                        mTeapot.getIndices());

                // disable the enabled arrays
                GLES20.glDisableVertexAttribArray(vertexHandle);
                GLES20.glDisableVertexAttribArray(textureCoordHandle);
            } else {
                GLES20.glDisable(GLES20.GL_CULL_FACE);
                GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                        false, 0, mBuildingsModel.getVertices());
                GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                        GLES20.GL_FLOAT, false, 0, mBuildingsModel.getTexCoords());

                GLES20.glEnableVertexAttribArray(vertexHandle);
                GLES20.glEnableVertexAttribArray(textureCoordHandle);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                        mTextures.get(3).mTextureID[0]);
                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                        modelViewProjection, 0);
                GLES20.glUniform1i(texSampler2DHandle, 0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0,
                        mBuildingsModel.getNumObjectVertex());

                SampleUtils.checkGLError("Renderer DrawBuildings");
            }

            SampleUtils.checkGLError("Render Frame");

        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

    }


    // The render function.
    private void audioRenderFrame()
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        State state = mRenderer.begin();
        mRenderer.drawVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // handle face culling, we need to detect if we are using reflection
        // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW); // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

        // did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
        {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();

            String obj_name = trackable.getName();

            Log.d(LOGTAG, "MRAY :: Found: " + obj_name);

            /**
             * Our targets array has been flattened to a string so will equal something like: ["one", "two"]
             * So, to stop weak matches such as 'two' within ["onetwothree", "two"] we wrap the term in
             * speech marks such as '"two"'
             **/
            Boolean looking_for = mTargets.toLowerCase().contains("\"" + obj_name.toLowerCase() + "\"");

            if (looking_for)
            {
                mActivity.imageFound(obj_name);
            }
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        mRenderer.end();
    }

    public void updateTargetStrings(String targets) {
        mTargets = targets;
    }

}
