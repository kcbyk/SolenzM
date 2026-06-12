package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.SongDao
import com.example.data.local.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Default curated YouTube suggestions list for Turkish music
    private val defaultCatalog = listOf(
        SongEntity(
            id = "7-q_lBebInY",
            title = "Antidepresan",
            artist = "Mabel Matiz & Mert Demir",
            durationSeconds = 210,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            coverUrl = "https://img.youtube.com/vi/7-q_lBebInY/hqdefault.jpg",
            downloadStatus = 0
        ),
        SongEntity(
            id = "QUnf_8_m6cM",
            title = "Ateşini Yolla Bana",
            artist = "Hakan Peker",
            durationSeconds = 245,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            coverUrl = "https://img.youtube.com/vi/QUnf_8_m6cM/hqdefault.jpg",
            downloadStatus = 0
        ),
        SongEntity(
            id = "Y4N-zZ_bXyM",
            title = "Fırtınadayım",
            artist = "Mabel Matiz",
            durationSeconds = 295,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            coverUrl = "https://img.youtube.com/vi/Y4N-zZ_bXyM/hqdefault.jpg",
            downloadStatus = 0
        ),
        SongEntity(
            id = "U_H50w6eQos",
            title = "Dudu",
            artist = "Tarkan",
            durationSeconds = 270,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            coverUrl = "https://img.youtube.com/vi/U_H50w6eQos/hqdefault.jpg",
            downloadStatus = 0
        ),
        SongEntity(
            id = "_O-9Rsh2k7I",
            title = "Affet",
            artist = "Müslüm Gürses",
            durationSeconds = 260,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            coverUrl = "https://img.youtube.com/vi/_O-9Rsh2k7I/hqdefault.jpg",
            downloadStatus = 0
        ),
        SongEntity(
            id = "47L58N967uU",
            title = "Senden Daha Güzel",
            artist = "Duman",
            durationSeconds = 230,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            coverUrl = "https://img.youtube.com/vi/47L58N967uU/hqdefault.jpg",
            downloadStatus = 0
        )
    )

    val allLocalSongs: Flow<List<SongEntity>> = songDao.getAllSongs()
    val downloadedSongs: Flow<List<SongEntity>> = songDao.getDownloadedSongs()
    val favoriteSongs: Flow<List<SongEntity>> = songDao.getFavoriteSongs()

    suspend fun getSongById(id: String): SongEntity? = withContext(Dispatchers.IO) {
        songDao.getSongById(id)
    }

    suspend fun saveToRoom(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.insertSong(song)
    }

    suspend fun toggleFavorite(song: SongEntity) = withContext(Dispatchers.IO) {
        val nextFav = !song.isFavorite
        songDao.updateFavoriteStatus(song.id, nextFav)
        val existing = songDao.getSongById(song.id)
        if (existing == null) {
            songDao.insertSong(song.copy(isFavorite = nextFav))
        }
    }

    private suspend fun viewModelResolveSongState(song: SongEntity): SongEntity {
        val roomTrack = songDao.getSongById(song.id)
        return roomTrack ?: song
    }

    /**
     * Searches YouTube using standard HTML results parsing. This mimics yt-dlp metadata indexing
     * client-side by extracting live video listings from public YouTube web responses.
     */
    suspend fun searchSongs(query: String): List<SongEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext defaultCatalog.map { viewModelResolveSongState(it) }
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val videoList = mutableListOf<SongEntity>()

        // 1. Primary: Try public Piped API search (extremely high performance, clean JSON, unblocked)
        try {
            val pipedResults = tryPipedSearch(encodedQuery)
            if (pipedResults.isNotEmpty()) {
                videoList.addAll(pipedResults)
                Log.d("MusicRepository", "Piped search successfully populated ${pipedResults.size} tracks")
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Piped search failed, trying Invidious", e)
        }

        // 2. Secondary Fallback: try public Invidious Search API
        if (videoList.isEmpty()) {
            try {
                val invidiousResults = tryInvidiousSearch(encodedQuery)
                if (invidiousResults.isNotEmpty()) {
                    videoList.addAll(invidiousResults)
                    Log.d("MusicRepository", "Invidious search fallback successfully populated ${invidiousResults.size} tracks")
                }
            } catch (ex: Exception) {
                Log.e("MusicRepository", "Invidious search fallback errored, trying standard scrape", ex)
            }
        }

        // 3. Tertiary Fallback: Try standard YouTube web scraping with consent-bypass headers (aborts instantly if redirected to loop)
        if (videoList.isEmpty()) {
            try {
                val url = "https://www.youtube.com/results?search_query=$encodedQuery"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                    .header("Cookie", "SOCS=CoBUEgo8ZXU_; CONSENT=YES+cb.20230531-04-p0.en+FX+904")
                    .header("Accept-Language", "tr,tr-TR;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()

                val noRedirectClient = okHttpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                val response = noRedirectClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val ytInitialDataStart = html.indexOf("var ytInitialData = {")
                    if (ytInitialDataStart != -1) {
                        val dataSub = html.substring(ytInitialDataStart)
                        val endBrace = dataSub.indexOf("};")
                        if (endBrace != -1) {
                            val rawJson = dataSub.substring("var ytInitialData = ".length, endBrace + 1)

                            val videoIds = mutableListOf<String>()
                            val titles = mutableListOf<String>()
                            val creators = mutableListOf<String>()
                            val durations = mutableListOf<String>()

                            val videoSeparator = "\"videoRenderer\":{"
                            var index = 0
                            while (true) {
                                val videoIndex = rawJson.indexOf(videoSeparator, index)
                                if (videoIndex == -1) break

                                val chunk = rawJson.substring(videoIndex, (videoIndex + 2500).coerceAtMost(rawJson.length))

                                val idMatch = "\"videoId\":\"([^\"]+)\"".toRegex().find(chunk)
                                val id = idMatch?.groupValues?.getOrNull(1) ?: ""

                                if (id.isNotEmpty() && !videoIds.contains(id) && videoIds.size < 15) {
                                    videoIds.add(id)

                                    val titleMatch = "\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"".toRegex().find(chunk)
                                    val titleScraped = titleMatch?.groupValues?.getOrNull(1)
                                        ?: "\"text\":\"([^\"]+)\"".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                                        ?: ""
                                    titles.add(decodeUnicodeEscapes(titleScraped))

                                    val ownerMatch = "\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"".toRegex().find(chunk)
                                    val ownerScraped = ownerMatch?.groupValues?.getOrNull(1) ?: "YouTube Sanatçı"
                                    creators.add(decodeUnicodeEscapes(ownerScraped))

                                    val lengthMatch = "\"lengthText\":\\{\"simpleText\":\"([^\"]+)\"".toRegex().find(chunk)
                                    val length = lengthMatch?.groupValues?.getOrNull(1) ?: "3:40"
                                    durations.add(length)
                                }
                                index = videoIndex + videoSeparator.length
                            }

                            for (i in 0 until videoIds.size) {
                                val vidId = videoIds[i]
                                val rawDuration = durations.getOrNull(i) ?: "3:40"
                                val parsedSecs = parseDurationToSeconds(rawDuration)

                                val title = titles.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "YouTube Video $i"
                                val artist = creators.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "Kanal $i"

                                val streamIdx = (vidId.hashCode().coerceAtLeast(0) % 10) + 1
                                val cleanStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$streamIdx.mp3"

                                videoList.add(
                                    SongEntity(
                                        id = vidId,
                                        title = title,
                                        artist = artist,
                                        durationSeconds = parsedSecs,
                                        streamUrl = cleanStreamUrl,
                                        coverUrl = "https://img.youtube.com/vi/$vidId/hqdefault.jpg",
                                        downloadStatus = 0
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Log.w("MusicRepository", "YouTube scrape returned unsuccessful code: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "YouTube standard web scraping error/redirect block", e)
            }
        }

        // 4. Final Fallback: local curated search if everything else fails
        if (videoList.isNotEmpty()) {
            videoList.map { viewModelResolveSongState(it) }
        } else {
            fallbackLocalSearch(query)
        }
    }

    private suspend fun tryPipedSearch(encodedQuery: String): List<SongEntity> {
        val instances = listOf(
            "pipedapi.adminforge.de",
            "pipedapi.astre.me",
            "pipedapi.lunar.icu",
            "pipedapi.synxt.ru",
            "pipedapi.kavin.rocks",
            "pipedapi.colby.id.au"
        )
        for (instance in instances) {
            try {
                Log.d("MusicRepository", "Trying Piped API search on $instance")
                val url = "https://$instance/search?q=$encodedQuery&filter=videos"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val list = mutableListOf<SongEntity>()
                    val jsonArray = JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val type = obj.optString("type")
                        if (type != "stream" && type != "video") continue
                        
                        val itemUrl = obj.optString("url") ?: ""
                        val videoId = if (itemUrl.startsWith("/watch?v=")) {
                            itemUrl.substring(9).substringBefore("&")
                        } else {
                            obj.optString("videoId")
                        }
                        
                        if (videoId.isEmpty()) continue
                        
                        val title = obj.optString("title") ?: "YouTube Video"
                        val artist = obj.optString("uploaderName") ?: obj.optString("author") ?: "YouTube Sanatçı"
                        
                        // Parse duration safely
                        var durationSeconds = 220
                        val durationVal = obj.opt("duration")
                        if (durationVal is Number) {
                            durationSeconds = durationVal.toInt()
                        } else if (durationVal is String) {
                            durationSeconds = parseDurationToSeconds(durationVal)
                        }

                        val streamIdx = (videoId.hashCode().coerceAtLeast(0) % 10) + 1
                        val cleanStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$streamIdx.mp3"
                        
                        list.add(
                            SongEntity(
                                id = videoId,
                                title = title,
                                artist = artist,
                                durationSeconds = durationSeconds,
                                streamUrl = cleanStreamUrl,
                                coverUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                downloadStatus = 0
                            )
                        )
                        if (list.size >= 15) break
                    }
                    if (list.isNotEmpty()) {
                        Log.d("MusicRepository", "Piped search success on $instance, found ${list.size} results")
                        return list
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Piped search fallback on $instance failed: ${e.message}")
            }
        }
        return emptyList()
    }

    private suspend fun tryInvidiousSearch(encodedQuery: String): List<SongEntity> {
        val instances = listOf(
            "invidious.nerdvpn.de",
            "yewtu.be",
            "vid.puffyan.us",
            "invidious.projectsegfaut.im",
            "inv.tux.im"
        )
        for (instance in instances) {
            try {
                Log.d("MusicRepository", "Trying Invidious API search fallback on $instance")
                val url = "https://$instance/api/v1/search?q=$encodedQuery&type=video"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val list = mutableListOf<SongEntity>()
                    val jsonArray = JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val type = obj.optString("type")
                        if (type != "video") continue
                        
                        val videoId = obj.optString("videoId") ?: ""
                        if (videoId.isEmpty()) continue
                        
                        val title = obj.optString("title") ?: "YouTube Video"
                        val author = obj.optString("author") ?: "YouTube Sanatçı"
                        val lengthSeconds = obj.optInt("lengthSeconds", 220)
                        
                        val streamIdx = (videoId.hashCode().coerceAtLeast(0) % 10) + 1
                        val cleanStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$streamIdx.mp3"
                        
                        list.add(
                            SongEntity(
                                id = videoId,
                                title = title,
                                artist = author,
                                durationSeconds = lengthSeconds,
                                streamUrl = cleanStreamUrl,
                                coverUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                downloadStatus = 0
                            )
                        )
                        if (list.size >= 15) break
                    }
                    if (list.isNotEmpty()) {
                        Log.d("MusicRepository", "Invidious search fallback success on $instance, found ${list.size} results")
                        return list
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Invidious fallback on $instance failed: ${e.message}")
            }
        }
        return emptyList()
    }

    private suspend fun fallbackLocalSearch(query: String): List<SongEntity> {
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        val filtered = defaultCatalog.filter {
            it.title.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
            it.artist.lowercase(Locale.getDefault()).contains(lowercaseQuery)
        }
        return filtered.map { viewModelResolveSongState(it) }
    }

    /**
     * Calls a real-time free suggestions Google Autocomplete API utilized by YouTube search.
     * Returns exact matches character-by-character.
     */
    suspend fun fetchSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=${URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val suggestions = mutableListOf<String>()
                val matches = "\"([^\"]+)\"".toRegex().findAll(body)
                var index = 0
                for (match in matches) {
                    val value = match.groupValues.getOrNull(1) ?: continue
                    if (index > 0 && 
                        value != query && 
                        value != "youtube" && 
                        !value.startsWith("http") && 
                        suggestions.size < 5
                    ) {
                        suggestions.add(value)
                    }
                    index++
                }
                suggestions
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Google suggestion query failed", e)
            emptyList()
        }
    }

    /**
     * Generate content completions dynamically using Google Gemini 3.5 AI Flash API
     * if the user desires artificial recommendations for musical completions.
     */
    suspend fun fetchGeminiAISuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY" || apiKey == "null") {
            return@withContext emptyList()
        }

        try {
            val systemInstructions = "Kullanıcı müzik uygulamasında arama kutusuna '$query' yazdı. Bu aramayı tamamlayabilecek en popüler Türkçe pop, rock veya rap şarkı/sanatçı aramalarından 4 adet öner." +
                    " Önerileri sadece her satıra bir öneri gelecek şekilde ve başında numara veya işaret olmadan, düz metin yaz."
            
            val jsonBody = """
                {
                  "contents": [
                    {
                      "parts": [
                        { "text": "$systemInstructions" }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(RequestBody.create(mediaType, jsonBody))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val textMatch = "\"text\":\\s*\"([^\"]+)\"".toRegex().find(responseBody)
                val textContent = textMatch?.groupValues?.getOrNull(1) ?: ""

                val rawLines = decodeUnicodeEscapes(textContent).split("\\n", "\n")
                rawLines.map { it.trim().replace("*", "").replace("-", "").trim() }
                    .filter { it.isNotBlank() && it.length > 2 && !it.contains("{") }
                    .take(4)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Gemini suggestion fetch failed", e)
            emptyList()
        }
    }

    /**
     * Download song or video physically into standard device Download folder, creating.
     */
    suspend fun downloadMedia(
        song: SongEntity,
        type: String, // "mp3" or "mp4"
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d("MusicRepository", "Starting physical download ($type) for video ID: ${song.id}")
            
            // Mark as downloading in Room so user gets feedback
            val updatedSong = song.copy(downloadStatus = 1, downloadType = type)
            songDao.insertSong(updatedSong)

            // Attempt to resolve real direct link via unified resolver
            val sourceUrl = getStreamOrDownloadUrl(song.id, isAudio = (type == "mp3"))
            Log.d("MusicRepository", "Resolved real URL for ($type) download: $sourceUrl")

            val request = Request.Builder().url(sourceUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("MusicRepository", "Server response failure code: ${response.code}")
                songDao.updateDownloadStatus(song.id, 0, null)
                return@withContext
            }

            val body = response.body
            if (body == null) {
                Log.e("MusicRepository", "Download body is empty")
                songDao.updateDownloadStatus(song.id, 0, null)
                return@withContext
            }

            // Create target directory in the public Download directory of the internal memory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val mytFolder = File(downloadsDir, "Solenz_Muzik")
            if (!mytFolder.exists()) {
                mytFolder.mkdirs()
            }

            val cleanTitle = song.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val cleanArtist = song.artist.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val extension = if (type == "mp4") "mp4" else "mp3"
            val targetFile = File(mytFolder, "$cleanTitle - $cleanArtist.$extension")

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        if (totalBytes > 0) {
                            val progress = bytesDownloaded.toFloat() / totalBytes
                            onProgress(progress)
                        }
                    }
                }
            }

            Log.d("MusicRepository", "Successfully downloaded media to phone path: ${targetFile.absolutePath}")
            
            // Register permanent complete paths and statuses inside our database
            songDao.insertSong(
                song.copy(
                    downloadStatus = 2,
                    downloadType = type,
                    localFilePath = targetFile.absolutePath
                )
            )
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed physical item download", e)
            songDao.updateDownloadStatus(song.id, 0, null)
        }
    }

    suspend fun deleteLocalFile(song: SongEntity) = withContext(Dispatchers.IO) {
        song.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        songDao.updateDownloadStatus(song.id, 0, null)
    }

    /**
     * Resolves high-fidelity live stream/download media links from YouTube utilizing the free Piped API instances,
     * fallback to Cobalt.tools API instance, and rotating public Invidious instances utilizing the latest_version audio/video streaming scheme.
     */
    suspend fun getStreamOrDownloadUrl(videoId: String, isAudio: Boolean): String = withContext(Dispatchers.IO) {
        // 1. Try Piped API streams endpoint first (extremely high performance, pristine direct link)
        val pipedStream = fetchPipedStreamUrl(videoId, isAudio)
        if (pipedStream != null && pipedStream.startsWith("http")) {
            Log.d("MusicRepository", "Successfully resolved Piped Stream URL for $videoId (audio=$isAudio): $pipedStream")
            return@withContext pipedStream
        }

        // 2. Try Cobalt Tools second (extremely reliable high-quality links)
        val cobalt = fetchCobaltUrl(videoId, isAudio)
        if (cobalt != null && cobalt.startsWith("http")) {
            Log.d("MusicRepository", "Successfully resolved Cobalt URL for $videoId (audio=$isAudio)")
            return@withContext cobalt
        }

        // 3. Fallback: Rotating public Invidious instances via direct latest_version format
        val instances = listOf(
            "invidious.nerdvpn.de",
            "yewtu.be",
            "vid.puffyan.us",
            "invidious.projectsegfaut.im",
            "inv.tux.im"
        )
        // Ensure same video gets same load-balanced instance
        val idx = (videoId.hashCode().coerceAtLeast(0) % instances.size)
        val selectedInstance = instances[idx]
        val itag = if (isAudio) "140" else "18" // Itag 140 = 128kbps AAC M4A audio, Itag 18 = 360p MP4 audio/video
        val invidiousUrl = "https://$selectedInstance/latest_version?id=$videoId&itag=$itag"
        Log.d("MusicRepository", "Piped & Cobalt failed, falling back to Invidious URL for $videoId (audio=$isAudio): $invidiousUrl")
        return@withContext invidiousUrl
    }

    /**
     * Tries resolving direct media streams dynamically utilizing rotating active Piped API instances.
     */
    suspend fun fetchPipedStreamUrl(videoId: String, isAudio: Boolean): String? = withContext(Dispatchers.IO) {
        val instances = listOf(
            "pipedapi.adminforge.de",
            "pipedapi.astre.me",
            "pipedapi.lunar.icu",
            "pipedapi.synxt.ru",
            "pipedapi.kavin.rocks",
            "pipedapi.colby.id.au"
        )
        for (instance in instances) {
            try {
                val url = "https://$instance/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (isAudio) {
                        val audioStreams = json.optJSONArray("audioStreams")
                        if (audioStreams != null && audioStreams.length() > 0) {
                            var bestUrl: String? = null
                            var bestBitrate = -1
                            for (i in 0 until audioStreams.length()) {
                                val stream = audioStreams.getJSONObject(i)
                                val streamUrl = stream.optString("url") ?: ""
                                if (streamUrl.startsWith("http")) {
                                    val streamFormat = stream.optString("format", "")
                                    val streamBitrate = stream.optInt("bitrate", -1)
                                    if (streamFormat.lowercase() == "m4a" || streamBitrate > bestBitrate) {
                                        bestUrl = streamUrl
                                        bestBitrate = streamBitrate
                                        if (streamFormat.lowercase() == "m4a") break // Prefer M4A directly
                                    }
                                }
                            }
                            if (bestUrl != null) return@withContext bestUrl
                        }
                    } else {
                        val videoStreams = json.optJSONArray("videoStreams")
                        if (videoStreams != null && videoStreams.length() > 0) {
                            for (i in 0 until videoStreams.length()) {
                                val stream = videoStreams.getJSONObject(i)
                                val streamUrl = stream.optString("url") ?: ""
                                val videoOnly = stream.optBoolean("videoOnly", false)
                                if (streamUrl.startsWith("http") && !videoOnly) {
                                    return@withContext streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Failed to resolve Piped Stream on $instance: ${e.message}")
            }
        }
        return@withContext null
    }

    /**
     * Resolves high-fidelity live stream/download media links from YouTube utilizing the free Cobalt.tools API instance.
     */
    suspend fun fetchCobaltUrl(videoId: String, isAudio: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://api.cobalt.tools/api/json"
            val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
            
            val requestBodyJson = """
                {
                  "url": "$youtubeUrl",
                  "downloadMode": "${if (isAudio) "audio" else "video"}",
                  "isAudioOnly": ${if (isAudio) "true" else "false"},
                  "audioFormat": "mp3",
                  "videoQuality": "720"
                }
            """.trimIndent()
            
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .post(RequestBody.create(mediaType, requestBodyJson))
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                Log.d("MusicRepository", "Cobalt API raw response: $bodyStr")
                val urlRegex = "\"url\":\"([^\"]+)\"".toRegex()
                val match = urlRegex.find(bodyStr)
                val directUrl = match?.groupValues?.getOrNull(1)
                if (directUrl != null) {
                    return@withContext directUrl.replace("\\/", "/")
                }
            } else {
                Log.e("MusicRepository", "Cobalt API failed with status code: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Cobalt API request errored", e)
        }
        return@withContext null
    }

    private fun decodeUnicodeEscapes(input: String): String {
        var text = input
        val regex = "\\\\u([0-9a-fA-F]{4})".toRegex()
        try {
            text = regex.replace(text) { matchResult ->
                val code = matchResult.groupValues[1].toInt(16)
                code.toChar().toString()
            }
            text = text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
        } catch (e: Exception) {
            // bypass
        }
        return text
    }

    private fun parseDurationToSeconds(durationStr: String): Int {
        return try {
            val parts = durationStr.split(":")
            when (parts.size) {
                1 -> parts[0].toIntOrNull() ?: 220
                2 -> {
                    val mins = parts[0].toIntOrNull() ?: 3
                    val secs = parts[1].toIntOrNull() ?: 40
                    mins * 60 + secs
                }
                3 -> {
                    val hours = parts[0].toIntOrNull() ?: 0
                    val mins = parts[1].toIntOrNull() ?: 3
                    val secs = parts[2].toIntOrNull() ?: 40
                    hours * 3600 + mins * 60 + secs
                }
                else -> 220
            }
        } catch (e: Exception) {
            220
        }
    }
}
