# RouterRemote

This is an extremely simple remote control app for DD-WRT routers. Currently all
it does is toggle on and off the OpenVPN client.

# How To Use

1. Open the ⋮ action menu and choose Settings
   1. Set the username and password you use to log in to your router's control
      panel
       - Note: Only BASIC authentication is supported; these credentials will be
         sent as plain text!
   2. Set the host for the router, e.g. 192.168.1.1
2. Go back to the main screen. The VPN On and Off buttons should now work
   (assuming you have set up the router's OpenVPN client)

# License

Apache 2.0
