package com.willkernel.app.waveview.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Created by willkernel on 2017/3/8.
 * mail:willkerneljc@gmail.com
 * <p>
 * http://bugly.qq.com/bbs/forum.php?mod=viewthread&tid=1180
 * http://blog.csdn.net/drkcore/article/details/51822818
 */
public class WaveView extends RenderView {
    private static final String TAG = "WaveView";
    private static final int SAMPLING_SIZE = 64;
    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    //采样点的X
    private float[] samplingX;
    //采样点位置均匀映射到[-2,2]的X
    private float[] mapX;
    private final Path firstPath = new Path();
    private final Path secondPath = new Path();
    //两条正弦波之间的波，振幅比较低的那一条
    private final Path centerPath = new Path();
    /**
     * 波峰和两条路径交叉点的记录，包括起点和终点，用于绘制渐变。
     * 通过日志可知其数量范围为7~9个，故这里size取9。
     * <p>
     * 每个元素都是一个float[2]，用于保存xy值
     */
    private final float[][] crestAndCrossPints = new float[9][2];

    //画布中心的高度
    private int centerHeight;
    //振幅
    private int amplitude;
    //用于处理矩形的rectF
    private final RectF rectF = new RectF();
    //绘图交叉模式。放在成员变量避免每次重复创建。
    private final Xfermode xfermode = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
    private final int backGroundColor = Color.rgb(24, 33, 41);
    private final int centerPathColor = Color.argb(64, 255, 255, 255);

    public WaveView(Context context) {
        this(context, null);
    }

    public WaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mPaint.setDither(true);
    }

    @Override
    protected void onRender(Canvas canvas, long millsPassed) {
        if (samplingX == null) {
            centerHeight = mHeight >> 1;
            amplitude = mWidth >> 3;
            samplingX = new float[SAMPLING_SIZE + 1];//因为包括起点和终点所以需要+1个位置
            mapX = new float[SAMPLING_SIZE + 1];
            float gap = mWidth / (float) SAMPLING_SIZE;//确定采样点之间的间距
            float x;
            for (int i = 0; i <= SAMPLING_SIZE; i++) {//
                x = i*gap;
                samplingX[i] = x;//x 坐标
                mapX[i] = (x / (float) mWidth) * 4 - 2;//将x映射到[-2,2]的区间上
            }
        }

        //背景
        canvas.drawColor(backGroundColor);

        //重置所有path并移到起点
        firstPath.rewind();
        secondPath.rewind();
        centerPath.rewind();
        firstPath.moveTo(0, centerHeight);
        secondPath.moveTo(0, centerHeight);
        centerPath.moveTo(0, centerHeight);

        //当前时间的偏移量，通过偏移量使得每次绘图都向右偏移，让画面动起来
        float offset = millsPassed / 500F;
        float x;
        float[] xy;
        //波形函数的值，包括上一点，当前点，下一点 的Y坐标值
        float lastV, curV = 0, nextV = (float) (amplitude * calcValue(mapX[0], offset));
        //波形函数的绝对值，用于筛选波峰和交错点
        float absLastV, absCurV, absNextV;
        //上一个筛选出的点是波峰还是交错点
        boolean lastIsCrest = false;
        //筛选出的波峰和交叉点的数量，包括起点和终点
        int crestAndCrossCount = 0;

        //遍历所有采样点
        for (int i = 0; i <= SAMPLING_SIZE; i++) {
            //计算采样点的位置
            x = samplingX[i];
            lastV = curV;
            curV = nextV;
            nextV = i < SAMPLING_SIZE ? (float) (amplitude * calcValue(mapX[i + 1], offset)) : 0;

            //连接路径
            firstPath.lineTo(x, centerHeight + curV);
            secondPath.lineTo(x, centerHeight - curV);
            //中间那条路径的振幅是上下的1/5
            centerPath.lineTo(x, centerHeight + curV / 5F);

            //记录极值点
            absLastV = Math.abs(lastV);
            absCurV = Math.abs(curV);
            absNextV = Math.abs(nextV);

            if (i == 0 || i == SAMPLING_SIZE || (lastIsCrest && absCurV < absLastV && absCurV < absNextV)) {/*上一个点为波峰，且该点是极小值点*/
                xy = crestAndCrossPints[crestAndCrossCount++];
                xy[0] = x;
                xy[1] = 0;
                lastIsCrest = false;
            } else if (!lastIsCrest && absCurV > absLastV && absCurV > absNextV) {/*上一点是交叉点，且该点极大值*/
                xy = crestAndCrossPints[crestAndCrossCount++];
                xy[0] = x;
                xy[1] = curV;
                lastIsCrest = true;
            }
        }

        Log.e(TAG, "mwidth=" + mWidth);
        //连接所以路径到终点
        firstPath.lineTo(mWidth, centerHeight);
        secondPath.lineTo(mWidth, centerHeight);
        centerPath.lineTo(mWidth, centerHeight);

        //记录layer
        int saveCount = canvas.saveLayer(0, 0, mWidth, mHeight, null, Canvas.ALL_SAVE_FLAG);
        //填充上下两条正弦函数
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(1);
        canvas.drawPath(firstPath, mPaint);
        canvas.drawPath(secondPath, mPaint);

        //绘制渐变
        mPaint.setColor(Color.BLUE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setXfermode(xfermode);
        float startX, crestY, endX;
        for (int i = 2; i < crestAndCrossCount; i += 2) {
            //每隔两个点可绘制一个矩形。这里先计算矩形的参数
            startX = crestAndCrossPints[i - 2][0];
            crestY = crestAndCrossPints[i - 1][1];
            endX = crestAndCrossPints[i][0];

            //crestY有正有负，无需去计算渐变是从上到下还是从下到上
            mPaint.setShader(new LinearGradient(0, centerHeight + crestY, 0, centerHeight - crestY, Color.BLUE, Color.GREEN, Shader.TileMode.CLAMP));
            rectF.set(startX, centerHeight + crestY, endX, centerHeight - crestY);
            canvas.drawRect(rectF, mPaint);
        }
        //清理
        mPaint.setShader(null);
        mPaint.setXfermode(null);

        //叠加layer，因为使用了SRC_IN的模式所以只会保留波形渐变重合的地方
        canvas.restoreToCount(saveCount);

        //绘制上弦线
        mPaint.setColor(Color.RED);
        mPaint.setStrokeWidth(3);
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(firstPath, mPaint);
        //绘制下弦线
        mPaint.setColor(Color.GREEN);
        canvas.drawPath(secondPath, mPaint);
        //绘制中间线
        mPaint.setColor(centerPathColor);
        canvas.drawPath(centerPath, mPaint);
    }

    /**
     * 图形
     * https://www.desmos.com/calculator/y52zsnxi4u
     * 正弦函数的公式是y=Asin（ωx+φ）+k，其中φ是偏移量，代码中通过 offset * Math.PI 来实现。
     * 这样我们只要在绘图的过程中将时间的流逝换算成偏移量即可实现波形的变换，配合Thread.sleep就可以倒腾出动画的效果。
     * <p>
     * x=[-2,2]
     * y=[-0.5,0.5]
     */
    private double calcValue(float x, float offset) {
        offset %= 2;
        double sinFunc = Math.sin(0.75 * Math.PI * x - offset * Math.PI);
        double dampingFunc = Math.pow(4 / (4 + Math.pow(x, 4)), 2.5);
        return dampingFunc * sinFunc;
    }
}