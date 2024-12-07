# Becker Binding

The Becker binding integrates windows and blinds attached to a [Becker CentralControl](https://www.becker-antriebe.com/de/smart-home/centralcontrol/) with OpenHAB.

## Supported Things

This binding should work with any CentralControl and the following device types:

 * roof windows
 * roller shutters
 * venetian blinds

In addition, this binding should also work with any device groups of these types.
It has been tested with [Roto Designo R6](https://www.roto-dachfenster.de/) connected to a Becker CentralControl CC41 using Centronics.
As Centronics allows neither positional movement nor feedback of the current position, these features are not supported.
Feel free to contact me if you own other devices and are willing to test them with snapshot builds.

## Discovery

The CentralControl has to be configured manually.
Once it is online, any attached devices and groups should be discovered automatically.

## Thing Configuration

### `CentralControl` Configuration

The CentralControl has to be configured manually using network address and port, and provides additional settings for fine tuning.
When changing these advanced settings, please keep in mind that *refreshInterval* also determines the maximum delay until new devices are discovered and old devices go to offline, and that *refreshInterval* must be set higher than *idleTimeout* or the connection might time out and will be reestablished periodically.

| Name            | Type    | Description                                                                     | Default | Required | Advanced |
|-----------------|---------|---------------------------------------------------------------------------------|---------|----------|----------|
| host               | text    | Network address or host name of the Becker CentralControl.                   |         | yes      | no       |
| port               | integer | Network port of the Becker CentralControl.                                   | 80      | no       | no       |
| connectionDelay    | integer | Time between binding initialization and first connection attempt.            | 1       | no       | yes      |
| connectionInterval | integer | Time between connection attempts.                                            | 60      | no       | yes      |
| refreshInterval    | integer | Time between requests to refresh information from the Becker CentralControl. | 600     | no       | yes      |
| connectionTimeout  | integer | Time before connection attempts are aborted.                                 | 10      | no       | yes      |
| requestTimeout     | integer | Time before pending requests are aborted.                                    | 10      | no       | yes      |
| idleTimeout        | integer | Time before connections are closed due to inactivity.                        | 3600    | no       | yes      |

### `Device` Configuration

Devices and groups can be added manually using the *id* assigned by the CentralControl.
This *id* can be found in the inbox of OpenHAB once the devices and groups are discovered.
It can also be found in the settings of receivers and groups in Beckers' CentralControl app.
 
| Name               | Type    | Description                                                                  | Default | Required | Advanced |
|--------------------|---------|------------------------------------------------------------------------------|---------|----------|----------|
| id                 | integer | The unique id assigned to this device by the Becker CentralControl.          |         | yes      | no       |

## Channels

The bridge currently has no channels. Devices and groups only have a single channel to control them:

| Channel | Type          | Read/Write | Description                                       |
|---------|---------------|------------|---------------------------------------------------|
| control | Rollershutter | RW         | Controls device movement using up, down and stop. |

Please note that this binding supports neither positional movement nor feedback of the current position.
Positional movement using percentages will be mapped to basic commands with 0% corresponding to UP and any other value to DOWN.
The current position reported by the channel will always correspond to the last command or percentage sent to it.

## Full Example

becker.things:

```
Bridge becker:bridge:home "Becker CentralControl CC41" [ host="1.2.3.4" ] {
        Thing roof-window livingroom [ id=12 ]
        Thing shutter livingroom [ id=13 ]
        Thing venetian livingroom [ id=14 ]
}
```

becker.items:

```
Rollershutter Livingroom_Windows { channel="becker:roof-window:home:livingroom:control" }
Rollershutter Livingroom_Shutters { channel="becker:shutter:home:livingroom:control" }
Rollershutter Livingroom_Blinds { channel="becker:venetian:home:livingroom:control" }
```
