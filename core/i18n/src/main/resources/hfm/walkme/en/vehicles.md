# Vehicles and maintenance
@about: Add a vehicle with its renewals, give it a maintenance schedule, and log each service.

## Open Vehicles {#open}
@screen: VEHICLES
@done: screen
@manual: vehicles#overview

In the menu, open **Home and family**, then **Vehicles**.

## Add a vehicle {#add}
@screen: VEHICLES
@target: vehicles.add
@done: shown vehicle.dialog
@manual: vehicles#vehicle-form

Click **Add a vehicle**.

## Describe it {#details}
@target: vehicle.dialog.save
@done: added vehicle
@manual: vehicles#details

- **Name**: what you call it, such as Civic.
- **Make**, **Model** and **Year**; **Energy**: **Electric** changes fuel to charges and leaves out engine tasks.
- **Registration renewal** and **Insurance renewal**: you are reminded before each.

Click **Save**.

## The odometer {#odometer}
@screen: VEHICLES VehicleTab.OVERVIEW
@target: vehicles.addReading
@manual: vehicles#enter-odometer

Click **Enter odometer** and type today's reading. Readings keep the maintenance due dates and forecasts right; the phone can send them too, with **Odometer or hours**.

## A maintenance schedule {#tasks}
@screen: VEHICLES VehicleTab.MAINTENANCE
@target: vehicles.starterTasks
@manual: vehicles#usual-tasks

On the **Maintenance** tab, click **Add usual tasks**: oil changes, tire rotation, winter tires on and off, brakes and filters, each with its interval in months or kilometres. Change or remove any of them, or **Add a task** of your own.

## Record a service {#service}
@screen: VEHICLES VehicleTab.MAINTENANCE
@target: vehicles.markDone
@done: shown vehicle.service
@manual: vehicles#record-done

When a task is done, click **Record as done** on it.

## The service {#service-form}
@target: vehicle.service.save
@manual: vehicles#service-form

Check the **Date** and **Odometer (km)**, tick the **Tasks done**, enter the **Garage or provider** and the **Cost**, and click **Save**. Each ticked task's schedule starts over, and the service is on the **Service log** tab.
