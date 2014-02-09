package net.md_5.bungee;

import com.google.common.base.Charsets;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.TimerTask;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ProxyServer;

@RequiredArgsConstructor
public class Metrics extends TimerTask
{

    private final ProxyServer proxy;
    private final String uuid;
    private boolean ping;

    @Override
    public void run()
    {
        try
        {
            post();
            ping = true;
        } catch ( IOException ex )
        {
            ProxyServer.getInstance().getLogger().info( "[Metrics] " + ex.getMessage() );
        }
    }

    private void post() throws IOException
    {
        // Data holder
        JsonObject data = new JsonObject();

        // Standard metrics
        data.addProperty( "guid", uuid );
        data.addProperty( "plugin_version", proxy.getVersion() );
        data.addProperty( "server_version", "0" );
        data.addProperty( "players_online", proxy.getOnlineCount() );

        // Extended metrics
        data.addProperty( "osname", System.getProperty( "os.name" ) );
        data.addProperty( "osarch", System.getProperty( "os.arch" ) );
        data.addProperty( "osversion", System.getProperty( "os.version" ) );
        data.addProperty( "cores", Runtime.getRuntime().availableProcessors() );
        data.addProperty( "auth_mode", ( proxy.getConfig().isOnlineMode() ) ? 1 : 0 );
        data.addProperty( "java_version", System.getProperty( "java.version" ) );

        // Ping is every subsequent update
        data.addProperty( "ping", ping );

        // GZIP the data to be nice to the metrics servers or something
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try ( GZIPOutputStream gzip = new GZIPOutputStream( b ) )
        {
            gzip.write( data.toString().getBytes( Charsets.UTF_8 ) );
        }
        byte[] compressed = b.toByteArray();

        // Open up a connection
        URLConnection con = new URL( "http://report.mcstats.org/plugin/BungeeCord" ).openConnection();

        // Set all the connection properties
        con.addRequestProperty( "User-Agent", "MCStats/7" );
        con.addRequestProperty( "Content-Type", "application/json" );
        con.addRequestProperty( "Content-Encoding", "gzip" );
        con.addRequestProperty( "Content-Length", Integer.toString( compressed.length ) );
        con.addRequestProperty( "Connection", "close" );

        // We need to be able to write out our data
        con.setDoOutput( true );

        // Write the data and then close the stream
        try ( OutputStream os = con.getOutputStream() )
        {
            os.write( compressed );
        }

        // Read in a single response line
        try ( BufferedReader br = new BufferedReader( new InputStreamReader( con.getInputStream() ) ) )
        {
            String line = br.readLine();
            // Server terminated connection or sent weird reply
            if ( line == null )
            {
                throw new IOException( "Did not receive response code" );
            }

            // Had to dig in the backend source for these values
            switch ( line )
            {
                // Everything is OK
                case "0":
                // Also OK, but this is our first request in 30 minutes.
                // TODO: I never get this value
                case "1":
                    break;
                // For some reason metrics wants us to make a new UUID
                // TODO: Handle this
                case "2":
                    break;
                // Damn, we did something wrong
                case "7":
                    // Format of this is 7,message
                    String errorMessage = ( line.length() > 2 ) ? line.substring( 2, line.length() ) : "Unknown error";
                    throw new IOException( errorMessage );
                default:
                    throw new IOException( "Unknown response code " + line );
            }
        }
    }
}
