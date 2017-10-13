package org.whispersystems.textsecuregcm.tests.storage;

import org.junit.Before;
import org.junit.Test;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccountTest {

  private final Device oldFirstDevice       = mock(Device.class);
  private final Device recentFirstDevice    = mock(Device.class);
  private final Device agingSecondaryDevice  = mock(Device.class);
  private final Device recentSecondaryDevice = mock(Device.class);
  private final Device oldSecondaryDevice    = mock(Device.class);

  @Before
  public void setup() {
    when(oldFirstDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(366));
    when(oldFirstDevice.isActive()).thenReturn(true);
    when(oldFirstDevice.getId()).thenReturn(1L);

    when(recentFirstDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
    when(recentFirstDevice.isActive()).thenReturn(true);
    when(recentFirstDevice.getId()).thenReturn(1L);

    when(agingSecondaryDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31));
    when(agingSecondaryDevice.isActive()).thenReturn(false);
    when(agingSecondaryDevice.getId()).thenReturn(2L);

    when(recentSecondaryDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
    when(recentSecondaryDevice.isActive()).thenReturn(true);
    when(recentSecondaryDevice.getId()).thenReturn(2L);

    when(oldSecondaryDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(366));
    when(oldSecondaryDevice.isActive()).thenReturn(false);
    when(oldSecondaryDevice.getId()).thenReturn(2L);
  }

  @Test
  public void testAccountActive() {
    Account recentAccount = new Account("+14152222222", new HashSet<Device>() {{
      add(recentFirstDevice);
      add(recentSecondaryDevice);
    }});

    assertTrue(recentAccount.isActive());

    Account oldSecondaryAccount = new Account("+14152222222", new HashSet<Device>() {{
      add(recentFirstDevice);
      add(agingSecondaryDevice);
    }});

    assertTrue(oldSecondaryAccount.isActive());

    Account agingPrimaryAccount = new Account("+14152222222", new HashSet<Device>() {{
      add(oldFirstDevice);
      add(agingSecondaryDevice);
    }});

    assertTrue(agingPrimaryAccount.isActive());
  }

  @Test
  public void testAccountInactive() {
    Account oldPrimaryAccount = new Account("+14152222222", new HashSet<Device>() {{
      add(oldFirstDevice);
      add(oldSecondaryDevice);
    }});

    assertFalse(oldPrimaryAccount.isActive());
  }

}
