package com.kanha.dev

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.load
import org.json.JSONObject

class DetailActivity : AppCompatActivity() {
 companion object {
  const val EXTRA_ITEM = "detail_item_json"
  fun intent(c: android.content.Context, item: JSONObject) =
   Intent(c, DetailActivity::class.java).putExtra(EXTRA_ITEM, item.toString())
 }
 private lateinit var data: JSONObject
 private lateinit var content: LinearLayout
 private lateinit var episodeArea: LinearLayout
 private var selectedTab = "Episodes"
 private var saved = false
 private var notified = false
 private var expanded = false
 private var listMode = false
 private var newestFirst = true
 private var selectedSeason = 1
 private var selectedEpisode = 1
 private var tmdbLoading = false
 private var tmdbError: String? = null
 private var tmdbBundle: TmdbDetailBundle? = null
 private var metaView: TextView? = null
 private var overviewView: TextView? = null
 private var genresView: TextView? = null
 private var creatorsView: TextView? = null
 private var episodeCount = 0
 private var aniListDetailsLoaded = false
 private val bg = 0xFF0B0B0F.toInt()
 private val surface = 0xFF16161C.toInt()
 private val panel = 0xFF20212A.toInt()
 private val secondary = 0xFFA7A7B2.toInt()
 private val red = 0xFFFF4D57.toInt()
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun title()=data.optString("title").takeIf{it.isNotBlank()&&it!="null"}?:"Devils' Crest"
 private fun episodes()=if(aniListDetailsLoaded) episodeCount else data.optInt("episodes",0).takeIf{it>0} ?: 0
 private fun meta():String {
  val year=data.optString("year").takeIf{it.isNotBlank()&&it!="null"}?:"2026"
  val status=data.optString("status").takeIf{it.isNotBlank()&&it!="null"}?:"Ongoing"
  val count=episodes().let{if(it>0) "$it Episodes" else "Episodes TBA"}
  return "$year  ·  $count  ·  $status"
 }
 private fun label(s:String,size:Float=15f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply{
  text=s;textSize=size;setTextColor(color);includeFontPadding=false
  typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
 }
 private fun shape(color:Int,r:Float=8f)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r.toInt()).toFloat()}
 private fun image(res:Int,size:Int)=ImageView(this).apply{
  setImageResource(res);scaleType=ImageView.ScaleType.FIT_CENTER
  layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
 }
 private fun gap(height:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(height))}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  window.statusBarColor=bg;window.navigationBarColor=bg
  data=runCatching{JSONObject(intent.getStringExtra(EXTRA_ITEM).orEmpty())}.getOrElse{JSONObject()}
  val scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true;clipToPadding=false}
  content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(16),dp(28))}
  // Expanded 320dp artwork with bottom scrim and floating back control.
  val hero=FrameLayout(this)
  val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel)}
  val banner=listOf("banner","backdrop","cover","image").firstNotNullOfOrNull{key->data.optString(key).takeIf{it.startsWith("http")}}
  if(banner!=null) art.load(banner){crossfade(true);allowHardware(false)}
  hero.addView(art,FrameLayout.LayoutParams(-1,-1))
  hero.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x22000000,0x00000000,0x660B0B0F,bg))},FrameLayout.LayoutParams(-1,-1))
  val back=FrameLayout(this).apply{background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(0x66000000)};setOnClickListener{finish()}}
  back.addView(image(R.drawable.detail_back,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  hero.addView(back,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.START).apply{topMargin=dp(14);leftMargin=dp(2)})
  content.addView(hero,LinearLayout.LayoutParams(-1,dp(320)))
  // Title wordmark: use the supplied title-logo URL when metadata provides one.
  val logoUrl=listOf("logo","logoUrl","titleLogo","title_logo").firstNotNullOfOrNull{key->data.optString(key).takeIf{it.startsWith("http")}}
  val titleView=label(title(),27f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END}
  if(logoUrl!=null){
   val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_START;load(logoUrl){allowHardware(false);crossfade(true)}}
   content.addView(logo,LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(8)})
  }else content.addView(titleView,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
  metaView=label(meta(),15f,secondary)
  content.addView(metaView,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12);bottomMargin=dp(18)})
  content.addView(primaryButton("Play",R.drawable.detail_play,Color.WHITE,Color.BLACK){toast("Install a streaming source to play this title")})
  content.addView(gap(10))
  content.addView(primaryButton("Download E1",R.drawable.detail_download,panel,Color.WHITE){toast("No download source available")})
  val sourceRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  sourceRow.addView(image(R.drawable.detail_hub,18).apply{alpha=.55f})
  sourceRow.addView(label("No sources installed",13f,0xFF6E6E78.toInt()).apply{setPadding(dp(7),0,0,0)})
  content.addView(sourceRow,LinearLayout.LayoutParams(-1,dp(34)).apply{topMargin=dp(8);bottomMargin=dp(10)})
  val synopsis=data.optString("description").takeIf{it.isNotBlank()&&it!="null"}?:""
  val overview=label(synopsis,15.5f,secondary).apply{maxLines=3;ellipsize=TextUtils.TruncateAt.END}
  overviewView=overview
  content.addView(overview)
  content.addView(label("Read more",13f,red,true).apply{
   setPadding(0,dp(8),0,dp(12))
   setOnClickListener{expanded=!expanded;overview.maxLines=if(expanded)Int.MAX_VALUE else 3;overview.ellipsize=if(expanded)null else TextUtils.TruncateAt.END;text=if(expanded)"Read less" else "Read more"}
  })
  val genres=data.optString("genres").takeIf{it.isNotBlank()&&it!="null"}?:""
  genresView=label(if(genres.isBlank()) "" else "Genres:  $genres",13f,secondary)
  content.addView(genresView)
  creatorsView=label(data.optString("creators").takeIf{it.isNotBlank()&&it!="null"}?.let{"Creators:  $it"}.orEmpty(),13f,secondary).apply{setPadding(0,dp(6),0,0)}
  content.addView(creatorsView)
  // Four icon actions use the exact PNG exports from the supplied kit.
  val actions=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER;setPadding(0,dp(20),0,dp(12))}
  val actionCells= mutableListOf<ImageView>()
  val actionData=listOf(Triple("My List",R.drawable.detail_add,R.drawable.detail_check),Triple("Notify",R.drawable.detail_notify,R.drawable.detail_notify_active),Triple("Share",R.drawable.detail_share,R.drawable.detail_share),Triple("Web",R.drawable.detail_web,R.drawable.detail_web))
  actionData.forEachIndexed{index,item->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
   val icon=ImageView(this).apply{setImageResource(item.second);scaleType=ImageView.ScaleType.FIT_CENTER}
   actionCells.add(icon);cell.addView(icon,LinearLayout.LayoutParams(dp(24),dp(24)))
   cell.addView(label(item.first,11f,secondary,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(6),0,0)})
   cell.setOnClickListener{when(index){
    0->{saved=!saved;icon.setImageResource(if(saved)item.third else item.second)}
    1->{notified=!notified;icon.setImageResource(if(notified)item.third else item.second)}
    2->{val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,title());startActivity(Intent.createChooser(share,"Share title"))}
    3->{val url=data.optString("url");if(url.startsWith("http"))startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))else toast("No external page available")}
   }}
   actions.addView(cell)
  }
  content.addView(actions)
  val tabs=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  listOf("Episodes","Cast","Relations","Details").forEach{tab->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER_HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(22)}}
   cell.addView(label(tab,15f,if(tab==selectedTab)red else secondary,tab==selectedTab).apply{setPadding(0,dp(10),0,dp(10));setOnClickListener{selectedTab=tab;refreshTabs(tabs);renderTab()}})
   val underline=View(this).apply{tag=tab;background=shape(red,2f);visibility=if(tab==selectedTab)View.VISIBLE else View.INVISIBLE}
   cell.addView(underline,LinearLayout.LayoutParams(dp(if(tab=="Episodes")58 else tab.length*8),dp(3)))
   tabs.addView(cell)
  }
  content.addView(tabs)
  episodeArea=LinearLayout(this).apply{orientation=1}
  content.addView(episodeArea)
  scroll.addView(content);setContentView(scroll);renderTab();loadTmdbDetails()
 }
 private fun primaryButton(caption:String,iconId:Int,color:Int,textColor:Int,onTap:()->Unit):View{
  val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER;background=shape(color,8f);setOnClickListener{onTap()}}
  row.addView(ImageView(this).apply{setImageResource(iconId);setColorFilter(textColor);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(24),dp(24)))
  row.addView(label(caption,16f,textColor,true).apply{setPadding(dp(10),0,0,0)})
  return row.apply{layoutParams=LinearLayout.LayoutParams(-1,dp(52))}
 }
 private fun refreshTabs(tabs:LinearLayout){
  for(i in 0 until tabs.childCount){val cell=tabs.getChildAt(i) as LinearLayout;val name=(cell.getChildAt(0) as TextView).text.toString();val active=name==selectedTab
   (cell.getChildAt(0) as TextView).setTextColor(if(active)red else secondary);cell.getChildAt(1).visibility=if(active)View.VISIBLE else View.INVISIBLE
  }
 }
 private fun renderTab(){
  episodeArea.removeAllViews()
  when(selectedTab){
   "Cast" -> renderCastTab()
   "Relations" -> renderRelationsTab()
   "Details" -> renderDetailsTab()
   else -> renderEpisodesTab()
  }
 }

 private fun renderEpisodesTab(){
  val heading=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  heading.addView(label("Episodes",22f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
  val toggle=FrameLayout(this).apply{
   background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(panel)}
   contentDescription=if(listMode) "Switch to thumbnail view" else "Switch to simple list"
   setOnClickListener{listMode=!listMode;renderTab()}
  }
  toggle.addView(image(if(listMode)R.drawable.detail_grid_view else R.drawable.detail_list_view,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  heading.addView(toggle,LinearLayout.LayoutParams(dp(36),dp(36)).apply{rightMargin=dp(8)})
  val more=FrameLayout(this).apply{
   background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(panel)}
   setOnClickListener{
    PopupMenu(this@DetailActivity,this).apply{
     val newest=menu.add("Newest first");val oldest=menu.add("Oldest first")
     newest.isCheckable=true;oldest.isCheckable=true;newest.isChecked=newestFirst;oldest.isChecked=!newestFirst
     setOnMenuItemClickListener{item->val next=item.title.toString()=="Newest first";if(newestFirst!=next){newestFirst=next;renderTab()};true}
    }.show()
   }
  }
  more.addView(image(R.drawable.detail_more,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  heading.addView(more,LinearLayout.LayoutParams(dp(36),dp(36)))
  episodeArea.addView(heading,LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(8);bottomMargin=dp(10)})
  if(episodes()<=0){episodeArea.addView(label("Episode information has not been announced yet.",14f,secondary));return}
  val episodeMap=tmdbBundle?.episodes.orEmpty().associateBy{it.number}
  val numbers=(1..episodes()).let{if(newestFirst)it.reversed() else it}
  if(listMode){
   numbers.forEach{n->
    val row=TextView(this).apply{text="Episode $n";textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,0,0);background=shape(if(n==selectedEpisode)0xFF343541.toInt() else panel,10f);setOnClickListener{selectedEpisode=n;renderTab();loadTmdbDetails()}}
    episodeArea.addView(row,LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(8)})
   }
  }else{
   numbers.forEach{n->
    val ep=episodeMap[n]
    val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,0,0,dp(15))}
    val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    val thumbFrame=FrameLayout(this).apply{background=shape(panel,10f);clipToOutline=true;layoutParams=LinearLayout.LayoutParams(dp(124),dp(72));setOnClickListener{selectedEpisode=n;renderTab();loadTmdbDetails()}}
    val thumb=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel);contentDescription=ep?.name ?: "Episode $n";val imageUrl=ep?.stillUrl ?: data.optString("cover").takeIf{it.startsWith("http")};if(imageUrl!=null)load(imageUrl){crossfade(true);allowHardware(false)}}
    thumbFrame.addView(thumb,FrameLayout.LayoutParams(-1,-1))
    if(ep!=null&&ep.rating>0)thumbFrame.addView(label("★ %.1f".format(ep.rating),10f,Color.WHITE,true).apply{background=shape(0xCC000000.toInt(),5f);setPadding(dp(5),dp(3),dp(5),dp(3))},FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply{leftMargin=dp(5);topMargin=dp(5)})
    thumbFrame.addView(image(R.drawable.detail_play,16).apply{setColorFilter(Color.WHITE);alpha=.9f},FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER))
    top.addView(thumbFrame)
    val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,dp(4),0);layoutParams=LinearLayout.LayoutParams(0,-2,1f);setOnClickListener{selectedEpisode=n;renderTab();loadTmdbDetails()}}
    info.addView(label("$n. ${ep?.name?.takeIf{it.isNotBlank()} ?: "Episode $n"}",15f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END})
    val meta=buildList{if((ep?.runtime ?: 0)>0)add("${ep!!.runtime} min");if(!ep?.airDate.isNullOrBlank())add(ep!!.airDate)}.joinToString("  ·  ")
    if(meta.isNotBlank())info.addView(label(meta,12f,secondary).apply{setPadding(0,dp(6),0,0)})
    top.addView(info)
    top.addView(ImageView(this).apply{setImageResource(R.drawable.detail_download);setColorFilter(Color.WHITE);scaleType=ImageView.ScaleType.FIT_CENTER;alpha=.9f;contentDescription="Download episode $n";setOnClickListener{toast("No download source available for episode $n")}},LinearLayout.LayoutParams(dp(24),dp(24)).apply{leftMargin=dp(5);rightMargin=dp(3)})
    card.addView(top)
    val synopsis=ep?.overview.orEmpty()
    if(synopsis.isNotBlank())card.addView(label(synopsis,13f,secondary).apply{typeface=Typeface.create("sans-serif",Typeface.ITALIC);maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(9),0,0)})
    episodeArea.addView(card,LinearLayout.LayoutParams(-1,-2))
   }
  }
 }
 private fun renderCastTab(){
  val heading=label("Episode $selectedEpisode Cast",20f,Color.WHITE,true)
  episodeArea.addView(heading,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(12)})
  if(tmdbLoading){episodeArea.addView(label("Loading cast from TMDB…",14f,secondary));return}
  val cast=tmdbBundle?.cast.orEmpty()
  if(cast.isEmpty()){
   episodeArea.addView(label(tmdbError ?: "No cast information available for this episode.",14f,secondary));return
  }
  val scroller=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;clipToPadding=false}
  val rail=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  cast.forEach{person->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(dp(104),-2).apply{rightMargin=dp(10)}}
   val photo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,10f);contentDescription=person.name}
   if(person.profileUrl!=null) photo.load(person.profileUrl){crossfade(true);allowHardware(false)}
   card.addView(photo,LinearLayout.LayoutParams(dp(104),dp(124)))
   card.addView(label(person.name,13f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(7),0,0)})
   if(person.character.isNotBlank()) card.addView(label(person.character,11f,secondary).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(3),0,0)})
   rail.addView(card)
  }
  scroller.addView(rail);episodeArea.addView(scroller,LinearLayout.LayoutParams(-1,-2))
  episodeArea.addView(label("Data provided by TMDB",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(14),0,0)})
 }

 private fun renderRelationsTab(){
  episodeArea.addView(label("Related titles",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(12)})
  if(tmdbLoading){episodeArea.addView(label("Finding related titles…",14f,secondary));return}
  val items=tmdbBundle?.relations.orEmpty()
  if(items.isEmpty()){episodeArea.addView(label(tmdbError ?: "No related titles available.",14f,secondary));return}
  val scroller=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;clipToPadding=false}
  val rail=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  items.forEach{item->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(dp(112),-2).apply{rightMargin=dp(12)}}
   val poster=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,10f);contentDescription=item.title}
   if(item.posterUrl!=null) poster.load(item.posterUrl){crossfade(true);allowHardware(false)}
   card.addView(poster,LinearLayout.LayoutParams(dp(112),dp(164)))
   card.addView(label(item.title,12f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(7),0,0)})
   rail.addView(card)
  }
  scroller.addView(rail);episodeArea.addView(scroller,LinearLayout.LayoutParams(-1,-2))
  episodeArea.addView(label("Recommendations and similar titles · TMDB",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(14),0,0)})
 }

 private fun renderDetailsTab(){
  episodeArea.addView(label("Information",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(10)})
  if(tmdbLoading){episodeArea.addView(label("Loading title details…",14f,secondary));return}
  val details=tmdbBundle?.details
  if(details==null){episodeArea.addView(label(tmdbError ?: "TMDB details are not available.",14f,secondary));return}
  val genres=details.optJSONArray("genres")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.joinToString(" · ")}.orEmpty()
  val studios=details.optJSONArray("production_companies")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.take(5).joinToString(" · ")}.orEmpty()
  val networks=details.optJSONArray("networks")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.take(4).joinToString(" · ")}.orEmpty()
  val date=details.optString("first_air_date").takeIf{it.isNotBlank()}?:details.optString("release_date")
  val rating=if(details.has("vote_average"))String.format("%.1f / 10",details.optDouble("vote_average")) else ""
  val language=details.optString("original_language").uppercase()
  val status=details.optString("status")
  val runtime=details.optInt("runtime").takeIf{it>0}?.let{"$it min"}?:""
  val rows=listOf("Genres" to genres,"Status" to status,"Release date" to date,"Rating" to rating,"Original language" to language,"Studios" to studios,"Networks" to networks,"Runtime" to runtime)
  rows.filter{it.second.isNotBlank()}.forEach{(key,value)->
   val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(10),0,dp(10))}
   row.addView(label(key.uppercase(),11f,0xFF777782.toInt(),true))
   row.addView(label(value,14f,Color.WHITE).apply{setPadding(0,dp(5),0,0)})
   episodeArea.addView(row,LinearLayout.LayoutParams(-1,-2))
   episodeArea.addView(View(this).apply{setBackgroundColor(0x22FFFFFF)},LinearLayout.LayoutParams(-1,dp(1)))
  }
  episodeArea.addView(label("Metadata provided by TMDB",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(12),0,0)})
 }

 private fun loadTmdbDetails(){
  tmdbLoading=true;tmdbError=null;renderTab()
  lifecycleScope.launch{
   try{
    val detailData=withContext(Dispatchers.IO){loadAniListDetailIfNeeded()}
    applyDetailMetadata(detailData)
    val result=withContext(Dispatchers.IO){
     val kind=data.optString("mediaType")
     if(kind=="anime" || kind=="manga" || kind=="novel" || kind.isBlank()) TmdbDetailRepository().load(title(),"tv","",selectedSeason,selectedEpisode)
     else TmdbDetailRepository().load(title(),kind,data.optString("id"),selectedSeason,selectedEpisode)
    }
    tmdbBundle=result
   }catch(error:Exception){tmdbError=error.message ?: "TMDB request failed"}
   tmdbLoading=false
   renderTab()
  }
 }

 private fun loadAniListDetailIfNeeded():JSONObject?{
  val kind=data.optString("mediaType")
  val id=data.optString("id").toIntOrNull() ?: return null
  if(kind !in listOf("anime","manga","novel")) return null
  val query="query(\$id:Int!){Media(id:\$id){id title{english romaji native} description(asHtml:false) episodes seasonYear status genres studios{nodes{name}} staff{edges{role node{name{full}}}}}}"
  val body=JSONObject().put("query",query).put("variables",JSONObject().put("id",id))
  val connection=java.net.URL("https://graphql.anilist.co").openConnection() as java.net.HttpURLConnection
  connection.requestMethod="POST";connection.connectTimeout=7000;connection.readTimeout=10000;connection.doOutput=true
  connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","application/json")
  connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
  val status=connection.responseCode
  val stream=if(status in 200..299) connection.inputStream else connection.errorStream
  val raw=stream.bufferedReader().use{it.readText()};connection.disconnect()
  if(status !in 200..299) throw IllegalStateException("AniList HTTP $status")
  return JSONObject(raw).optJSONObject("data")?.optJSONObject("Media")
 }

 private fun applyDetailMetadata(item:JSONObject?){
  if(item==null) return
  val names=item.optJSONObject("title")
  val correctTitle=listOf("english","romaji","native").map{names?.optString(it).orEmpty()}.firstOrNull{it.isNotBlank()&&it!="null"}
  if(!correctTitle.isNullOrBlank()) data.put("title",correctTitle)
  val description=item.optString("description").takeIf{it.isNotBlank()&&it!="null"}.orEmpty()
  val genres=item.optJSONArray("genres")?.let{arr->(0 until arr.length()).map{arr.optString(it)}.filter{it.isNotBlank()}.joinToString(" · ")}.orEmpty()
  val studios=item.optJSONObject("studios")?.optJSONArray("nodes")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.joinToString(" · ")}.orEmpty()
  val staff=item.optJSONObject("staff")?.optJSONArray("edges")
  val creators=staff?.let{arr->(0 until arr.length()).mapNotNull{index->
   val edge=arr.optJSONObject(index)?:return@mapNotNull null
   val role=edge.optString("role").lowercase()
   if(role.contains("director")||role.contains("creator")||role.contains("series composition")) edge.optJSONObject("node")?.optJSONObject("name")?.optString("full")?.takeIf{n->n.isNotBlank()} else null
  }.distinct().take(5).joinToString(" · ")}.orEmpty()
  episodeCount=item.optInt("episodes",0).takeIf{it>0}?:0
  aniListDetailsLoaded=true
  if(item.optInt("seasonYear")>0) data.put("year",item.optInt("seasonYear").toString())
  if(item.optString("status").isNotBlank()) data.put("status",item.optString("status"))
  data.put("description",description).put("genres",genres).put("creators",creators)
  overviewView?.text=description
  genresView?.text=if(genres.isBlank()) "" else "Genres:  $genres"
  creatorsView?.text=if(creators.isBlank()) if(studios.isBlank()) "" else "Studios:  $studios" else "Creators:  $creators"
  metaView?.text=meta()
 }

}
