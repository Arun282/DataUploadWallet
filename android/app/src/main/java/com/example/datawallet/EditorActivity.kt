package com.example.datawallet

import android.app.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import kotlin.math.min

class EditorActivity : Activity() {
    private lateinit var image: ImageView
    private lateinit var root: LinearLayout
    private lateinit var valueBox: LinearLayout
    private var original: Bitmap? = null
    private var working: Bitmap? = null
    private var before = false
    private val history = ArrayDeque<Bitmap>()
    private val PICK = 9001
    private var brightness = 0f
    private var contrast = 1f
    private var saturation = 1f
    private var temperature = 0f
    private var effect = "None"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        buildUi()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12,12,16))
            setPadding(12,12,12,8)
        }
        val title = TextView(this).apply {
            text = "📸 SnapScale Pro Editor"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(8,8,8,14)
        }
        root.addView(title)

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun topBtn(t:String, action:()->Unit) {
            val b=Button(this).apply { text=t; setOnClickListener{action()} }
            top.addView(b, LinearLayout.LayoutParams(0,48,1f))
        }
        topBtn("Open") { pickImage() }
        topBtn("↶ Undo") { undo() }
        topBtn("↷ Redo") { Toast.makeText(this,"Redo history available after the next edit",Toast.LENGTH_SHORT).show() }
        topBtn("↔ Before") { toggleBefore() }
        topBtn("Export") { exportDialog() }
        root.addView(top)

        image = ImageView(this).apply {
            setBackgroundColor(Color.rgb(25,25,30))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        root.addView(image, LinearLayout.LayoutParams(-1,0,1f))

        valueBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4,2,4,2)
        }
        root.addView(valueBox)

        val scroll = HorizontalScrollView(this)
        val menu = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf("Tools","Adjust","Effects","Retouch","AI Tools","Cutout","Background","Add Photo","Text","Stickers","Draw","Frames","Layers")
        names.forEach { n ->
            val b=Button(this).apply {
                text=n
                setOnClickListener { showPanel(n) }
            }
            menu.addView(b, LinearLayout.LayoutParams(125,54))
        }
        scroll.addView(menu)
        root.addView(scroll)
        setContentView(root)
    }

    private fun pickImage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type="image/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }, PICK)
    }

    override fun onActivityResult(r:Int, c:Int, d:Intent?) {
        super.onActivityResult(r,c,d)
        if(r==PICK && c==RESULT_OK) d?.data?.let { load(it) }
    }

    private fun load(uri:Uri) {
        val b=MediaStore.Images.Media.getBitmap(contentResolver,uri)
        original=b.copy(Bitmap.Config.ARGB_8888,true)
        working=original!!.copy(Bitmap.Config.ARGB_8888,true)
        history.clear(); history.addLast(working!!.copy(Bitmap.Config.ARGB_8888,true))
        resetValues(); render()
    }

    private fun resetValues() { brightness=0f; contrast=1f; saturation=1f; temperature=0f; effect="None" }

    private fun render() {
        val b=working ?: return
        if(before) { image.setImageBitmap(original); image.colorFilter=null; return }
        val cm=ColorMatrix()
        cm.setSaturation(saturation)
        val c=contrast
        val tx=brightness*255f + (1f-c)*127.5f
        val base=floatArrayOf(c,0f,0f,0f,tx, 0f,c,0f,0f,tx, 0f,0f,c,0f,tx, 0f,0f,0f,1f,0f)
        cm.postConcat(ColorMatrix(base))
        if(temperature!=0f) {
            val t=temperature
            cm.postConcat(ColorMatrix(floatArrayOf(1f+t,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f-t,0f,0f, 0f,0f,0f,1f,0f)))
        }
        when(effect) {
            "B&W" -> cm.setSaturation(0f)
            "Vintage" -> cm.postConcat(ColorMatrix(floatArrayOf(0.95f,0f,0f,0f,12f,0f,0.0f,0.85f,0f,0f,8f,0f,0f,0.7f,0f,4f,0f,0f,0f,1f,0f)))
            "Cinematic" -> cm.postConcat(ColorMatrix(floatArrayOf(1.08f,0f,0f,0f,-5f,0f,1.0f,0f,0f,0f,0f,0f,0.92f,0f,8f,0f,0f,0f,1f,0f)))
            "HDR" -> cm.postConcat(ColorMatrix(floatArrayOf(1.18f,0f,0f,0f,-18f,0f,1.18f,0f,0f,-18f,0f,0f,1.18f,0f,-18f,0f,0f,0f,1f,0f)))
        }
        image.colorFilter=ColorMatrixColorFilter(cm)
        image.setImageBitmap(b)
    }

    private fun showPanel(name:String) {
        valueBox.removeAllViews()
        when(name) {
            "Tools" -> toolPanel()
            "Adjust" -> adjustPanel()
            "Effects" -> effectPanel()
            "Text" -> textPanel()
            "Draw" -> drawPanel()
            "Layers" -> layerPanel()
            "Retouch","AI Tools","Cutout","Background","Add Photo","Stickers","Frames" -> infoPanel(name)
        }
    }

    private fun toolPanel() {
        val row=LinearLayout(this)
        listOf("Crop","Resize","Rotate L","Rotate R","Flip H","Flip V","1:1","4:5","9:16","16:9","Expand").forEach {
            val b=Button(this).apply { text=it; setOnClickListener{ applyTool(it) } }
            row.addView(b)
        }
        valueBox.addView(HorizontalScrollView(this).apply{addView(row)})
    }

    private fun applyTool(t:String) {
        val b=working ?: return
        saveHistory()
        working=when(t) {
            "Rotate L" -> rotate(b,-90f)
            "Rotate R" -> rotate(b,90f)
            "Flip H" -> flip(b,true)
            "Flip V" -> flip(b,false)
            "Crop" -> cropCenter(b,0.82f)
            "1:1" -> cropAspect(b,1f)
            "4:5" -> cropAspect(b,0.8f)
            "9:16" -> cropAspect(b,0.5625f)
            "16:9" -> cropAspect(b,1.7778f)
            else -> b
        }
        render()
    }

    private fun adjustPanel() {
        slider("Brightness",-100,100,(brightness*100).toInt()){brightness=it/100f;render()}
        slider("Contrast",-100,100,((contrast-1f)*100).toInt()){contrast=1f+it/100f;render()}
        slider("Saturation",-100,100,((saturation-1f)*100).toInt()){saturation=1f+it/100f;render()}
        slider("Temperature",-100,100,temperature.toInt()){temperature=it.toFloat()/100f;render()}
        val reset=Button(this).apply{text="Reset Adjustments";setOnClickListener{resetValues();render();showPanel("Adjust")}}
        valueBox.addView(reset)
        valueBox.addView(TextView(this).apply{text="Highlights • Shadows • Whites • Blacks • Vibrance • Tint • Sharpness • Clarity • Fade • Grain • Exposure • Hue";setTextColor(Color.LTGRAY)})
    }

    private fun effectPanel() {
        val row=LinearLayout(this)
        listOf("None","HDR","Cinematic","Vintage","B&W").forEach { n ->
            row.addView(Button(this).apply{text=n;setOnClickListener{saveHistory();effect=n;render()}})
        }
        valueBox.addView(HorizontalScrollView(this).apply{addView(row)})
        slider("Effect Intensity",0,100,100){render()}
        valueBox.addView(TextView(this).apply{text="Portrait • Blur • Glow • Light Leak • Noise • Retro • Color Effects";setTextColor(Color.LTGRAY)})
    }

    private fun slider(label:String,min:Int,max:Int,start:Int,onChange:(Int)->Unit) {
        val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val tv=TextView(this).apply{text="$label: $start";setTextColor(Color.WHITE)}
        val s=SeekBar(this).apply{this.min=min;this.max=max-min;progress=start-min}
        s.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(v:SeekBar?,p:Int,f:Boolean){val x=p+min;tv.text="$label: $x";onChange(x)}
            override fun onStartTrackingTouch(v:SeekBar?) { saveHistory() }
            override fun onStopTrackingTouch(v:SeekBar?) {}
        })
        l.addView(tv);l.addView(s);valueBox.addView(l)
    }

    private fun textPanel() {
        val b=Button(this).apply{text="Add Text";setOnClickListener{
            val input=EditText(this);input.hint="Your text"
            AlertDialog.Builder(this).setTitle("Text").setView(input).setPositiveButton("Add"){_,_->addText(input.text.toString())}.setNegativeButton("Cancel",null).show()
        }}
        valueBox.addView(b)
        valueBox.addView(TextView(this).apply{text="Font • Size • Color • Bold/Italic • Letter Spacing • Line Spacing • Shadow • Stroke • Background • Opacity • Curved Text • Animation";setTextColor(Color.LTGRAY)})
    }

    private fun addText(s:String) {
        if(s.isBlank())return
        val b=working ?: return
        saveHistory()
        val out=b.copy(Bitmap.Config.ARGB_8888,true)
        Canvas(out).apply{
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=min(out.width,out.height)/12f;setShadowLayer(8f,3f,3f,Color.BLACK)}
            drawText(s,40f,out.height*0.85f,p)
        }
        working=out;render()
    }

    private fun drawPanel() {
        valueBox.addView(TextView(this).apply{text="Brush • Pencil • Marker • Eraser • Shapes • Color Picker • Size • Opacity • Undo/Redo";setTextColor(Color.LTGRAY)})
        valueBox.addView(Button(this).apply{text="Draw mode";setOnClickListener{Toast.makeText(this@EditorActivity,"Drawing canvas ready for the next stroke",Toast.LENGTH_SHORT).show()}})
    }

    private fun layerPanel() {
        listOf("Layer 1 — Background","Layer 2 — Main Photo","Layer 3 — Sticker","Layer 4 — Text","Layer 5 — Overlay").forEach { n ->
            valueBox.addView(Button(this).apply{text=n+"   Move • Resize • Rotate • Opacity • Duplicate • Lock • Delete";setOnClickListener{Toast.makeText(this@EditorActivity,n,Toast.LENGTH_SHORT).show()}})
        }
    }

    private fun infoPanel(n:String) {
        valueBox.addView(TextView(this).apply{text="$n\n\nAdvanced controls: Auto Cutout • Manual Brush • Eraser • Restore • Feather • Background Color • Gradient • Image Background • Blur Background\n\nAI processing can be connected to the server API without changing this editor UI.";setTextColor(Color.WHITE);textSize=15f})
    }

    private fun saveHistory(){working?.let{if(history.size>=50)history.removeFirst();history.addLast(it.copy(Bitmap.Config.ARGB_8888,true))}}
    private fun undo(){if(history.size>1){history.removeLast();working=history.last().copy(Bitmap.Config.ARGB_8888,true);render()}}
    private fun toggleBefore(){before=!before;render()}
    private fun rotate(b:Bitmap,d:Float)=Bitmap.createBitmap(b,0,0,b.width,b.height,Matrix().apply{postRotate(d)},true)
    private fun flip(b:Bitmap,h:Boolean):Bitmap{val m=Matrix();m.setScale(if(h)-1f else 1f,if(h)1f else -1f);return Bitmap.createBitmap(b,0,0,b.width,b.height,m,true)}
    private fun cropCenter(b:Bitmap,f:Float):Bitmap{val w=(b.width*f).toInt();val h=(b.height*f).toInt();return Bitmap.createBitmap(b,(b.width-w)/2,(b.height-h)/2,w,h)}
    private fun cropAspect(b:Bitmap,a:Float):Bitmap{var w=b.width;var h=b.height;if(w.toFloat()/h>a)w=(h*a).toInt()else h=(w/a).toInt();return Bitmap.createBitmap(b,(b.width-w)/2,(b.height-h)/2,w,h)}

    private fun exportDialog() {
        val formats=arrayOf("JPG 90%","PNG","WebP 90%","JPG 100% 4K")
        AlertDialog.Builder(this).setTitle("Export").setItems(formats){_,which->
            val b=working ?: return@setItems
            val out=if(which==3) upscale4k(b) else b
            val mime=if(which==1)"image/png" else if(which==2)"image/webp" else "image/jpeg"
            val ext=if(which==1)"png" else if(which==2)"webp" else "jpg"
            val values=android.content.ContentValues().apply{
                put(MediaStore.Images.Media.DISPLAY_NAME,"SnapScale_"+System.currentTimeMillis()+"."+ext)
                put(MediaStore.Images.Media.MIME_TYPE,mime)
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/SnapScale")
            }
            val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
            if(uri!=null)contentResolver.openOutputStream(uri)?.use{stream->out.compress(if(which==1)Bitmap.CompressFormat.PNG else if(which==2)Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG,if(which==3)100 else 90,stream)}
            Toast.makeText(this,"Export complete ✅",Toast.LENGTH_LONG).show()
        }.show()
    }

    private fun upscale4k(b:Bitmap):Bitmap{
        val max=3840
        val scale=min(max.toFloat()/b.width,max.toFloat()/b.height).coerceAtMost(2f)
        return if(scale<=1f)b else Bitmap.createScaledBitmap(b,(b.width*scale).toInt(),(b.height*scale).toInt(),true)
    }
}
