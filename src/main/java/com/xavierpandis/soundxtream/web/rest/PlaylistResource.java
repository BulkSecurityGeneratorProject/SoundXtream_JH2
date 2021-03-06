package com.xavierpandis.soundxtream.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.sun.org.apache.xpath.internal.operations.Bool;
import com.xavierpandis.soundxtream.domain.Playlist;
import com.xavierpandis.soundxtream.domain.Song;
import com.xavierpandis.soundxtream.domain.Song_user;
import com.xavierpandis.soundxtream.domain.User;
import com.xavierpandis.soundxtream.repository.PlaylistRepository;
import com.xavierpandis.soundxtream.repository.SeguimientoRepository;
import com.xavierpandis.soundxtream.repository.UserRepository;
import com.xavierpandis.soundxtream.repository.search.PlaylistSearchRepository;
import com.xavierpandis.soundxtream.security.SecurityUtils;
import com.xavierpandis.soundxtream.web.rest.dto.ActivityDTO;
import com.xavierpandis.soundxtream.web.rest.dto.SongDTO;
import com.xavierpandis.soundxtream.web.rest.util.HeaderUtil;
import com.xavierpandis.soundxtream.web.rest.util.PaginationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.social.google.api.plus.Activity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;

/**
 * REST controller for managing Playlist.
 */
@RestController
@RequestMapping("/api")
public class PlaylistResource {

    private final Logger log = LoggerFactory.getLogger(PlaylistResource.class);

    @Inject
    private PlaylistRepository playlistRepository;

    @Inject
    private PlaylistSearchRepository playlistSearchRepository;

    @Inject
    private UserRepository userRepository;

    @Inject
    private SeguimientoRepository seguimientoRepository;

    /**
     * POST  /playlists -> Create a new playlist.
     */
    @RequestMapping(value = "/playlists",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Playlist> createPlaylist(@Valid @RequestBody Playlist playlist) throws URISyntaxException {
        log.debug("REST request to save Playlist : {}", playlist);
        if (playlist.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("playlist", "idexists", "A new playlist cannot already have an ID")).body(null);
        }
        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin()).get();
        playlist.setUser(user);
        ZonedDateTime today = ZonedDateTime.now();
        playlist.setDateCreated(today);
        Playlist result = playlistRepository.save(playlist);
        playlistSearchRepository.save(result);
        return ResponseEntity.created(new URI("/api/playlists/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert("playlist", result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /playlists -> Updates an existing playlist.
     */
    @RequestMapping(value = "/playlists",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Playlist> updatePlaylist(@Valid @RequestBody Playlist playlist) throws URISyntaxException {
        log.debug("REST request to update Playlist : {}", playlist);
        if (playlist.getId() == null) {
            return createPlaylist(playlist);
        }
        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin()).get();
        playlist.setUser(user);
        ZonedDateTime today = ZonedDateTime.now();
        playlist.setDateCreated(today);
        Playlist result = playlistRepository.save(playlist);
        playlistSearchRepository.save(result);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert("playlist", playlist.getId().toString()))
            .body(result);
    }

    /**
     * GET  /playlists -> get all the playlists.
     */
    @RequestMapping(value = "/playlists",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Playlist>> getAllPlaylists(Pageable pageable)
        throws URISyntaxException {
        log.debug("REST request to get a page of Playlists");
        Page<Playlist> page = playlistRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/playlists");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET  /playlists/:id -> get the "id" playlist.
     */
    @RequestMapping(value = "/playlists/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Playlist> getPlaylist(@PathVariable Long id) {
        log.debug("REST request to get Playlist : {}", id);
        Playlist playlist = playlistRepository.findOneWithEagerRelationships(id);
        return Optional.ofNullable(playlist)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /playlists/:id -> delete the "id" playlist.
     */
    @RequestMapping(value = "/playlists/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long id) {
        log.debug("REST request to delete Playlist : {}", id);
        playlistRepository.delete(id);
        playlistSearchRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("playlist", id.toString())).build();
    }

    /**
     * SEARCH  /_search/playlists/:query -> search for the playlist corresponding
     * to the query.
     */
    @RequestMapping(value = "/_search/playlists/{query:.+}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<Playlist> searchPlaylists(@PathVariable String query) {
        log.debug("REST request to search Playlists for query {}", query);
        return StreamSupport
            .stream(playlistSearchRepository.search(queryStringQuery(query)).spliterator(), false)
            .collect(Collectors.toList());
    }

    @RequestMapping(value = "/song/{id}/playlists",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Playlist>> getPlaylistWithSong(@PathVariable Long id) {
        log.debug("REST request to get Playlist : {}", id);
        List<Playlist> playlists = playlistRepository.findAllWithEagerRelationships();
        List<Playlist> playlistWithSong = new ArrayList<>();

        for(Playlist playlist:playlists){
            for(Song song:playlist.getSongs()){
                if(song.getId() == id){
                    playlistWithSong.add(playlist);
                }
            }
        }

        return new ResponseEntity<>(
            playlistWithSong,
            HttpStatus.OK);
    }

    @RequestMapping(value = "/playlistsApp",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Playlist>> getPlaylistsApp(Pageable pageable)
        throws URISyntaxException {
        log.debug("REST request to get a page of Songs");
        Page<Playlist> page = playlistRepository.findAll(pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/songs");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @RequestMapping(value = "/activityFollowing",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<ActivityDTO>> getAllActivityFollowing(Pageable pageable) throws URISyntaxException {

        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin()).get();

        Page<Object[]> page = seguimientoRepository.findAllActFollowing(user.getLogin(),pageable);

        //DTO donde guardo luego la fusion de canciones y playlists
        ArrayList<ActivityDTO> listActivity = new ArrayList<>();

        // DTO para guardar canciones y playlists
        ArrayList<ActivityDTO> listActivityTracks = new ArrayList<>();
        ArrayList<ActivityDTO> listActivityPlaylists = new ArrayList<>();
        ArrayList<ActivityDTO> listActivityShare = new ArrayList<>();

        for (Object o[] : page) {
            Song c = (Song) o[0];

            ActivityDTO activityDTO1 = new ActivityDTO();
            activityDTO1.setType("song");
            activityDTO1.setSong(c);
            activityDTO1.setDate(c.getDate_posted());

            Boolean existTrack = false;
            Boolean existPlaylist = false;
            Boolean existShare = false;

            for(int k = 0; k < listActivityTracks.size();k++){
                if(listActivityTracks.get(k).getSong().getId() == c.getId()){
                    existTrack = true;
                }
            }
            if(!existTrack){
                listActivityTracks.add(activityDTO1);
            }

            Playlist l = (Playlist) o[1];

            ActivityDTO activityDTO2 = new ActivityDTO();
            activityDTO2.setType("playlist");
            activityDTO2.setPlaylist(l);
            activityDTO2.setDate(l.getDateCreated());

            for(int k = 0; k < listActivityPlaylists.size();k++){
                if(listActivityPlaylists.get(k).getPlaylist().getId() == l.getId()){
                    existPlaylist = true;
                }
            }
            if(!existPlaylist){
                listActivityPlaylists.add(activityDTO2);
            }

            Song_user su = (Song_user) o[2];

            ActivityDTO activityDTO3 = new ActivityDTO();
            activityDTO3.setType("share");
            activityDTO3.setShareTrack(su);
            activityDTO3.setDate(su.getSharedDate());

            for(int k = 0; k < listActivityShare.size();k++){
                if(listActivityShare.get(k).getShareTrack().getId() == su.getId()){
                    existShare = true;
                }
            }
            if(!existShare){
                listActivityShare.add(activityDTO3);
            }

            //listActivity.add(activityDTO2);
           // System.out.printf("\nPlaylist: %s \n Tracks: %s",l,c);
        }

        for(int i = 0; i < listActivityPlaylists.size();i++){
            listActivity.add(listActivityPlaylists.get(i));
        }
        for(int i = 0; i < listActivityTracks.size();i++){
            listActivity.add(listActivityTracks.get(i));
        }

        for(int i = 0; i < listActivityShare.size();i++){
            listActivity.add(listActivityShare.get(i));
        }

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/songs");
        return new ResponseEntity<>(listActivity, headers, HttpStatus.OK);
    }

    @RequestMapping(value = "/playlistUser/{login}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<Playlist>> getPlaylistsUser(@PathVariable String login, Pageable pageable) throws URISyntaxException {

        Page<Playlist> page = playlistRepository.findPlaylistsUser(login,pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/playlistUser");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

}
