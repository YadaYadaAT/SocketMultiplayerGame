package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.gui.controllers.*;
import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;


public class GomokuFXApp extends Application {


    @Override
    public void start(Stage stage) throws Exception {

        // Creates the view navigator which will manage scene switching between different FXML views.
        var viewNavigator = new GomokuFXViewNavigator();

        // Preload the LOGIN view FXML and its associated controller.//TODO: will change into an intro scene
        // This ensures the root node and controller are created now, so switching to it later is instant.
        viewNavigator.preload(View.SCENEWRAPPER);
        viewNavigator.preload(View.LOGIN);
        viewNavigator.preload(View.SIGNUP);
        viewNavigator.preload(View.LOBBY);
        viewNavigator.preload(View.GAME);

        // Create a shared data object that all controllers will reference.
        // Holds things like username, nickname, relogCode, player stats, pending invites, and login state.
        // This object is accessed by multiple threads (network listener thread writes, FX thread reads), so fields
        // are volatile or synchronized;  where needed (to be honest there wasn't a need so far to synchronize anything in there
        //yet since only callbacks write and javathreads read...with an exception of a constructor , no way to have sync issues)
        var data = new GomokuFXCommonToAllControllersData();


        // Create the low-level network adapter to handle TCP communication with the server.
        // "localhost" and port 999 indicate the server address. It opens a socket and starts a listener thread internally.
        // This thread constantly reads incoming packets and triggers callbacks when packets arrive.
        // It's obvious what we also use to send packets;(ObjectInputStream wraps ) more about it on the clientNetworkAdapater
        // class itself
        var cna = new ClientNetworkAdapterImpl("localhost", 999);


        // For the client network adapter class itself :
        // Create a higher-level network handler that wraps the adapter and provides controller-friendly callbacks.
        // Also holds a reference to the shared data object; while most data are maintained by the callback in controllers.
        // things like lastServerActivity time (long) is being send from there to the field in the data (since centralized)
        //For the stage being passed down :
        // passing the stage to a field so we can have access to it in case we want to nuke the session
        //(alternately you could also clear/reset every controller from  session data
        // but here we took the bug free guarantee solution to just nuke the objects(no GC problem...rarely happens)
        var networkHandler = new GomokuFXNetworkHandler(cna, data , stage);

        // Set the LoginController reference in the network handler so that LOGIN_RESPONSE packets can be delivered to it.
        // The controller was preloaded by the navigator, so we fetch it from the navigator's controller map.
        // (Currently we are about to have like 4-5 maximum controllers with 1:1 fxml, if it scales we
        // might swap to a map or something... currently we set one by one.
        networkHandler.setWrapperCtrl((WrapperController) viewNavigator.getController(View.SCENEWRAPPER));
        networkHandler.setLoginCtrl((LoginController) viewNavigator.getController(View.LOGIN));
        networkHandler.setSignupCtrl((SignupController) viewNavigator.getController(View.SIGNUP));
        networkHandler.setLobbyCtrl((LobbyController) viewNavigator.getController(View.LOBBY));
        networkHandler.setGameCtrl((GameController) viewNavigator.getController(View.GAME));

        // Initialize all controllers with references to:
        // - The view navigator (so they can switch views)
        // - The network handler (so they can send packets)
        // - The shared data object (so they can read/write shared state)
        // This calls the `init()` method on each controller(Currently through Base controller)...,
        //   ...setting up all internal references.
        viewNavigator.initControllers(viewNavigator, networkHandler, data);

        // After controllers are initialized, set the network adapter's listener callback.
        // This binds a single callback handler  in the network adapter that processes all incoming packets.
        // When a packet arrives, it calls handleServerPacket() in this network handler, which delegates to
        // the correct corresponding callback methods inside of each controller.
        // (currently only the info_response packet goes to more than 1 controller, due to being generic)
        networkHandler.initCallbackHandler();
        // Test connection (well a bit of a lie ^^ we just want to trigger the callback to update
        // the header ui )
        networkHandler.sendHandshake();
        //get wrapper (i take it from roots; been included like the rest for harmony)
        //Although a bit abnormal in the sense that its the wrapper, we will exclude it from swapping at the goTo
        Parent wrapper = viewNavigator.getWrapper();

        // setTheContentPane after having the wrapper preload
        viewNavigator.setTheContentPane();

        // Create the JavaFX Scene and attach the wrapper root.
        // The wrapper stays constant; only its contentPane will be swapped when switching views.
        stage.setScene(new Scene(wrapper));

        // Set the initial view in the contentPane (LOGIN)
        viewNavigator.getContentPane().getChildren().setAll(viewNavigator.getRoot(View.LOGIN));

        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png"))));

        // Set the window title. ~YadaYada~ <-You  put this title; => bounds to be good...
        stage.setTitle("YadaYada Gomoku 2026");

        // Set the window width.
        stage.setWidth(1200);

        // Set the window height .
        stage.setHeight(800);
        stage.setResizable(true);

        // Show the window on the screen.
        // At this point, the FX thread takes control and renders the scene.
        // All UI updates from network callbacks must happen on the FX thread using Platform.runLater().
        // (or at least they should ;D big chance one or two will escape from my attention ^^
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

}
